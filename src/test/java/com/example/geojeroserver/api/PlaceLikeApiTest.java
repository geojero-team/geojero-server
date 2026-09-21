package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.geojeroserver.auth.SessionCookies;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 맛집 · 숙소 · 카페 하트 API(V51, 2026-09-22 사용자: *"숙소/카페/맛집도 스팟과 같이 좋아요 기능 있었으면 좋겠어"*).
 *
 * 규칙은 스팟 하트(V49 · SpotLikeApiTest)와 같다 — 보기는 비로그인, 누르기 · 취소만 로그인,
 * 한 사람이 한 곳에 하나, 두 번 눌러도 같은 답. 다른 점은 대상이 places 라는 것뿐이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceLikeApiTest {
  static final String TEST_USERS = "test-oauth-placelikes-";
  static final String ALICE = TEST_USERS + "alice";
  static final String BOB = TEST_USERS + "bob";
  static final String GONE = TEST_USERS + "gone";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;

  @BeforeEach
  void resetLikes() {
    jdbc.update("""
        DELETE FROM place_likes WHERE user_id IN
          (SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?)""",
        TEST_USERS + "%");
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?", TEST_USERS + "%");
  }

  @Test void 토큰_없이_누르기와_취소는_401() throws Exception {
    long id = place("FOOD");
    mvc.perform(put("/api/places/{id}/like", id)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/places/{id}/like", id)).andExpect(status().isUnauthorized());
    assertEquals(0, likeRows(id));
  }

  @Test void 위조_토큰으로_누르면_401() throws Exception {
    like("1.9999999999.deadbeef", place("CAFE")).andExpect(status().isUnauthorized());
  }

  /** 토큰 서명은 users 행을 보지 않는다 — 지운 계정의 토큰은 FK 위반 500 이 아니라 401 이다(스팟과 같다). */
  @Test void 삭제된_계정의_토큰으로_누르면_401() throws Exception {
    long uid = userId(GONE);
    String token = sessions.issueToken(uid);
    jdbc.update("DELETE FROM users WHERE user_id = ?", uid);
    like(token, place("STAY")).andExpect(status().isUnauthorized());
  }

  /** 우리 목록에 없는 곳은 404 다 — 스팟(POI_NOT_FOUND)과 같은 자리의 답이다. */
  @Test void 없는_곳은_404() throws Exception {
    like(token(ALICE), 999_999_999L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
  }

  @Test void 누르면_200_두_번_눌러도_행은_하나_다른_사람이_누르면_수만_는다() throws Exception {
    long id = place("FOOD");
    for (int i = 0; i < 2; i++) {
      like(token(ALICE), id)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.placeId").value((int) id))
          .andExpect(jsonPath("$.likeCount").value(1))
          .andExpect(jsonPath("$.liked").value(true));
    }
    assertEquals(1, likeRows(id));

    like(token(BOB), id)
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.liked").value(true));
    assertEquals(2, likeRows(id));
  }

  @Test void 취소하면_liked_false_안_누른_것을_취소해도_200() throws Exception {
    long id = place("CAFE");
    like(token(ALICE), id).andExpect(status().isOk());
    for (int i = 0; i < 2; i++) {
      unlike(token(ALICE), id)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(0))
          .andExpect(jsonPath("$.liked").value(false));
    }
    assertEquals(0, likeRows(id));
  }

  /** 목록 · 상세는 비로그인으로 수를 주고, 토큰이 있을 때만 liked 가 참이다(깨진 토큰이어도 200). */
  @Test void 목록과_상세의_likeCount는_비로그인_liked는_토큰으로만() throws Exception {
    long id = place("FOOD");
    like(token(ALICE), id).andExpect(status().isOk());

    for (String auth : new String[] {null, "Bearer 1.9999999999.deadbeef"}) {
      list("FOOD", auth)
          .andExpect(status().isOk())
          .andExpect(jsonPath(item(id, "likeCount")).value(1))
          .andExpect(jsonPath(item(id, "liked")).value(false));
      detail(id, auth)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(1))
          .andExpect(jsonPath("$.liked").value(false));
    }
    list("FOOD", "Bearer " + token(ALICE)).andExpect(jsonPath(item(id, "liked")).value(true));
    detail(id, "Bearer " + token(ALICE)).andExpect(jsonPath("$.liked").value(true));
  }

  // ── 거들 ────────────────────────────────────────────────────────────────

  private long place(String kind) {
    return jdbc.queryForObject("SELECT content_id FROM places WHERE kind = ? ORDER BY sort_order LIMIT 1",
        Long.class, kind);
  }

  private int likeRows(long id) {
    return jdbc.queryForObject("SELECT count(*) FROM place_likes WHERE place_id = ?", Integer.class, id);
  }

  private long userId(String oauthId) {
    var rows = jdbc.queryForList("SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id = ?",
        Long.class, oauthId);
    if (!rows.isEmpty()) return rows.getFirst();
    return jdbc.queryForObject(
        "INSERT INTO users (provider, oauth_id, nickname) VALUES ('KAKAO', ?, ?) RETURNING user_id",
        Long.class, oauthId, oauthId);
  }

  private String token(String oauthId) {
    return sessions.issueToken(userId(oauthId));
  }

  private ResultActions like(String token, long id) throws Exception {
    return mvc.perform(put("/api/places/{id}/like", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions unlike(String token, long id) throws Exception {
    return mvc.perform(delete("/api/places/{id}/like", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions list(String kind, String auth) throws Exception {
    var req = get("/api/places").param("kind", kind);
    if (auth != null) req = req.header(HttpHeaders.AUTHORIZATION, auth);
    return mvc.perform(req);
  }

  private ResultActions detail(long id, String auth) throws Exception {
    var req = get("/api/places/{id}", id);
    if (auth != null) req = req.header(HttpHeaders.AUTHORIZATION, auth);
    return mvc.perform(req);
  }

  private static String item(long id, String field) {
    return "$.places[?(@.placeId == %d)].%s".formatted(id, field);
  }
}
