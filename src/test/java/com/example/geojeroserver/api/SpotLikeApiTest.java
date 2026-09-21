package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.geojeroserver.auth.SessionCookies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 스팟 하트 API(V49, 2026-09-21 사용자 결정 · 디자인브리프 부록 Q) — 로컬 Postgres 로 왕복한다.
 *
 * 스팟 목록의 「추천순」이 하트 수 순이 되도록, 로그인한 사람이 스팟에 하트를 누르고(PUT) 취소한다(DELETE).
 * 목록(`GET /api/pois`)과 상세(`GET /api/pois/{id}`)는 비로그인으로 하트 수를 주고, 토큰이 있으면 내가 눌렀는지도 준다.
 * 로그인은 users 행을 넣고 SessionCookies 로 토큰을 만들어 흉내 낸다(VisitorPhotoApiTest 와 같은 방식).
 * poi_id 는 하드코딩하지 않는다 — 로컬 DB 는 공유라 하트가 한 개도 없는 스팟을 골라 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpotLikeApiTest {
  /** 이 접두사로 만든 계정만 지운다. */
  static final String TEST_USERS = "test-oauth-spotlikes-";
  static final String ALICE = TEST_USERS + "alice";
  static final String BOB = TEST_USERS + "bob";
  static final String GONE = TEST_USERS + "gone";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;

  @BeforeEach
  void resetLikes() {
    jdbc.update("""
        DELETE FROM spot_likes WHERE user_id IN
          (SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?)""",
        TEST_USERS + "%");
  }

  @AfterAll
  void cleanup() {
    // spot_likes 는 ON DELETE CASCADE 로 함께 정리
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?",
        TEST_USERS + "%");
  }

  // ── 인증: Spring Security 가 permitAll 이라 컨트롤러가 막는다 ─────────────────

  /** 쓰기 핸들러에서 require() 한 줄만 빠져도 로그인 없는 하트가 열린다. 누르기·취소 둘 다 지킨다. */
  @Test void 토큰_없이_누르기와_취소는_401() throws Exception {
    long poi = spotPoi();
    mvc.perform(put("/api/pois/{poiId}/like", poi)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/pois/{poiId}/like", poi)).andExpect(status().isUnauthorized());
    assertEquals(0, likeRows(poi));
  }

  @Test void 위조_토큰으로_누르기와_취소는_401() throws Exception {
    long poi = spotPoi();
    like("1.9999999999.deadbeef", poi).andExpect(status().isUnauthorized());
    unlike("1.9999999999.deadbeef", poi).andExpect(status().isUnauthorized());
    assertEquals(0, likeRows(poi));
  }

  /**
   * 토큰 서명은 users 행을 보지 않는다(7일 유효). 계정을 지운 뒤의 토큰으로 누르면
   * FK 위반 500 이 아니라 401 이어야 한다(/api/me · 방문자 사진 올리기와 같은 답).
   */
  @Test void 삭제된_계정의_토큰으로_누르면_401() throws Exception {
    long uid = userId(GONE);
    String token = sessions.issueToken(uid);
    jdbc.update("DELETE FROM users WHERE user_id = ?", uid);

    like(token, spotPoi()).andExpect(status().isUnauthorized());
    assertEquals(0, (int) jdbc.queryForObject(
        "SELECT count(*) FROM spot_likes WHERE user_id = ?", Integer.class, uid));
  }

  // ── 대상: 화면 스팟 19곳만 ─────────────────────────────────────────────────

  /**
   * 없는 poi · 화면에 나오지 않는 poi(theme NULL — 명사해수욕장 등) · 고현터미널은 404 다.
   * 고현터미널은 방문자 사진 칸은 있지만(V22) 스팟 목록에 없으므로 하트 대상이 아니다 — 사진과 다르다.
   */
  @Test void 없는_poi와_화면_스팟이_아닌_poi와_고현터미널은_404() throws Exception {
    for (long poi : new long[] {missingPoi(), nonSpotPoi(), terminalPoi()}) {
      like(token(ALICE), poi)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("POI_NOT_FOUND"));
      unlike(token(ALICE), poi)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("POI_NOT_FOUND"));
    }
    assertEquals(0, testLikeRows());
  }

  // ── 누르기 · 취소 ──────────────────────────────────────────────────────────

  /** 누르면 200 이고 그 스팟의 하트 수와 내가 눌렀다는 사실이 온다. 두 번 눌러도 같은 답이고 행은 하나다. */
  @Test void 누르면_200_liked_true_두_번_눌러도_같은_답_행은_하나() throws Exception {
    long poi = spotPoi();
    String token = token(ALICE);

    for (int i = 0; i < 2; i++) {
      var res = like(token, poi)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.poiId").value((int) poi))
          .andExpect(jsonPath("$.likeCount").value(1))
          .andExpect(jsonPath("$.liked").value(true))
          .andReturn();
      assertEquals(LIKE_KEYS, fieldNames(json(res)));
    }
    assertEquals(1, likeRows(poi));

    // 다른 사람이 누르면 수만 는다 — 내가 눌렀는지는 토큰마다 따로다
    like(token(BOB), poi)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.liked").value(true));
    assertEquals(2, likeRows(poi));
  }

  /** 취소하면 200 이고 liked false · 줄어든 수가 온다. 안 눌렀던 상태에서 취소해도 200 이다(두 번 취소 포함). */
  @Test void 취소하면_liked_false_두_번_취소도_200() throws Exception {
    long poi = spotPoi();
    like(token(ALICE), poi).andExpect(status().isOk());
    like(token(BOB), poi).andExpect(status().isOk());

    for (int i = 0; i < 2; i++) {
      var res = unlike(token(ALICE), poi)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.poiId").value((int) poi))
          .andExpect(jsonPath("$.likeCount").value(1)) // BOB 의 하트는 그대로다
          .andExpect(jsonPath("$.liked").value(false))
          .andReturn();
      assertEquals(LIKE_KEYS, fieldNames(json(res)));
    }
    assertEquals(1, likeRows(poi));
    assertEquals(0, (int) jdbc.queryForObject(
        "SELECT count(*) FROM spot_likes WHERE poi_id = ? AND user_id = ?", Integer.class,
        poi, userId(ALICE)));

    unlike(token(BOB), poi)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.liked").value(false));
    assertEquals(0, likeRows(poi));
  }

  // ── 목록 · 상세: 하트 수는 비로그인, liked 는 토큰으로만 ────────────────────

  /**
   * 목록의 likeCount 가 누르기·취소를 따라 증감한다. liked 는 토큰으로만 갈린다 —
   * 토큰이 없거나 깨졌거나 만료됐어도 목록은 200 이다(require() 금지, 방문자 사진의 isMine 과 같은 규칙).
   */
  @Test void 목록의_likeCount가_증감하고_liked는_토큰으로_갈리고_깨진_토큰도_200() throws Exception {
    long poi = spotPoi();
    long alice = userId(ALICE);
    long now = Instant.now().getEpochSecond();

    list(null)
        .andExpect(jsonPath(item(poi, "likeCount")).value(0))
        .andExpect(jsonPath(item(poi, "liked")).value(false));

    like(token(ALICE), poi).andExpect(status().isOk());
    like(token(BOB), poi).andExpect(status().isOk());

    list("Bearer " + token(ALICE))
        .andExpect(jsonPath(item(poi, "likeCount")).value(2))
        .andExpect(jsonPath(item(poi, "liked")).value(true));
    // 이 방식으로 서명한 토큰이 통한다는 것을 먼저 보인다 — 아래 만료 토큰이 서명 탓에 떨어지는 게 아니다
    list("Bearer " + signedToken(alice, now + 60))
        .andExpect(jsonPath(item(poi, "liked")).value(true));

    for (String bad : new String[] {null, "Bearer 1.9999999999.deadbeef",
        "Bearer " + signedToken(alice, now - 60)}) {
      list(bad)
          .andExpect(jsonPath(item(poi, "likeCount")).value(2))
          .andExpect(jsonPath(item(poi, "liked")).value(false));
    }

    unlike(token(ALICE), poi).andExpect(status().isOk());
    list("Bearer " + token(ALICE))
        .andExpect(jsonPath(item(poi, "likeCount")).value(1))
        .andExpect(jsonPath(item(poi, "liked")).value(false));
    list("Bearer " + token(BOB))
        .andExpect(jsonPath(item(poi, "likeCount")).value(1))
        .andExpect(jsonPath(item(poi, "liked")).value(true));
  }

  /** withImages=true 경로는 항목을 다시 만든다(withImageUrl) — 거기서 하트 필드를 잃으면 홈 지도 목록이 0 으로 나간다. */
  @Test void 목록_withImages_경로도_하트_필드를_잃지_않는다() throws Exception {
    long poi = spotPoi();
    like(token(ALICE), poi).andExpect(status().isOk());

    mvc.perform(get("/api/pois?withImages=true")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(item(poi, "likeCount")).value(1))
        .andExpect(jsonPath(item(poi, "liked")).value(true));
  }

  /** 상세도 목록과 같은 값이다 — 두 화면이 다른 수를 말하면 안 된다. */
  @Test void 상세도_같은_likeCount와_liked를_준다() throws Exception {
    long poi = spotPoi();

    detail(poi, null)
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.liked").value(false));

    like(token(ALICE), poi).andExpect(status().isOk());
    like(token(BOB), poi).andExpect(status().isOk());

    detail(poi, "Bearer " + token(ALICE))
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.liked").value(true));
    detail(poi, null)
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.liked").value(false));
    detail(poi, "Bearer 1.9999999999.deadbeef")
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.liked").value(false));

    unlike(token(BOB), poi).andExpect(status().isOk());
    detail(poi, "Bearer " + token(BOB))
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.liked").value(false));
  }

  // ── 준비 ──────────────────────────────────────────────────────────────────

  /** 하트 응답은 이 셋뿐이다 — 누가 눌렀는지(userId · 닉네임)는 내려주지 않는다. */
  static final Set<String> LIKE_KEYS = Set.of("poiId", "likeCount", "liked");

  ResultActions like(String token, long poiId) throws Exception {
    return mvc.perform(put("/api/pois/{poiId}/like", poiId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  ResultActions unlike(String token, long poiId) throws Exception {
    return mvc.perform(delete("/api/pois/{poiId}/like", poiId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  /** 목록. 토큰 자리는 Authorization 헤더 값 전체(null 이면 헤더 없음) — 깨진 값도 그대로 보내려고. */
  ResultActions list(String authorization) throws Exception {
    var req = get("/api/pois");
    if (authorization != null) {
      req.header(HttpHeaders.AUTHORIZATION, authorization);
    }
    return mvc.perform(req).andExpect(status().isOk());
  }

  /** 목록에서 그 스팟 한 줄의 필드 — 순서(poi_id)가 아니라 id 로 찾는다. 한 건이라 .value() 가 풀어 읽는다. */
  static String item(long poiId, String field) {
    return "$.pois[?(@.poiId == " + poiId + ")]." + field;
  }

  ResultActions detail(long poiId, String authorization) throws Exception {
    var req = get("/api/pois/{poiId}", poiId);
    if (authorization != null) {
      req.header(HttpHeaders.AUTHORIZATION, authorization);
    }
    return mvc.perform(req).andExpect(status().isOk());
  }

  String token(String oauthId) {
    return sessions.issueToken(userId(oauthId));
  }

  /** 컨텍스트 빈의 서명키로 만료 시각을 골라 서명한다. SessionCookies 에는 만료를 고르는 공개 메서드가 없다. */
  String signedToken(long uid, long expEpochSec) throws Exception {
    byte[] key = (byte[]) ReflectionTestUtils.getField(sessions, "key");
    String payload = uid + "." + expEpochSec;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return payload + "." + HexFormat.of().formatHex(
        mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }

  static JsonNode json(MvcResult res) throws Exception {
    return new ObjectMapper().readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  static Set<String> fieldNames(JsonNode node) {
    var names = new java.util.HashSet<String>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  long userId(String oauthId) {
    return jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, oauthId);
  }

  /**
   * 화면에 나오는 스팟(theme IS NOT NULL) 중 **지금 하트가 한 개도 없는** 곳. 수를 단언하는 테스트가
   * 로컬 DB 에 있던 남의 하트에 흔들리지 않게 한다. 호출 시점 기준이라 같은 스팟이 필요하면 한 번 받아 둔다.
   */
  long spotPoi() {
    var ids = jdbc.queryForList("""
        SELECT poi_id FROM pois p WHERE theme IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM spot_likes l WHERE l.poi_id = p.poi_id)
        ORDER BY poi_id LIMIT 1""", Long.class);
    if (ids.isEmpty()) {
      fail("하트가 없는 화면 스팟이 없다 — 로컬 DB 의 spot_likes 가 스팟을 전부 채웠다. 단언을 풀지 말고 그 행을 확인할 것");
    }
    return ids.get(0);
  }

  long nonSpotPoi() {
    return jdbc.queryForObject(
        "SELECT poi_id FROM pois WHERE theme IS NULL AND poi_kind <> 'TERMINAL' ORDER BY poi_id LIMIT 1",
        Long.class);
  }

  long terminalPoi() {
    return jdbc.queryForObject("SELECT poi_id FROM pois WHERE poi_kind = 'TERMINAL'", Long.class);
  }

  long missingPoi() {
    return jdbc.queryForObject("SELECT COALESCE(max(poi_id), 0) + 1000 FROM pois", Long.class);
  }

  private int likeRows(long poiId) {
    return jdbc.queryForObject("SELECT count(*) FROM spot_likes WHERE poi_id = ?",
        Integer.class, poiId);
  }

  /** 이 테스트 계정들의 하트 행 수. @BeforeEach 가 비우므로 거부 뒤에는 0 이어야 한다. */
  private int testLikeRows() {
    return jdbc.queryForObject("""
        SELECT count(*) FROM spot_likes l JOIN users u ON u.user_id = l.user_id
        WHERE u.provider = 'KAKAO' AND u.oauth_id LIKE ?""",
        Integer.class, TEST_USERS + "%");
  }
}
