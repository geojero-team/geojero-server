package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geojeroserver.auth.KakaoGateway;
import com.example.geojeroserver.auth.SessionCookies;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 카카오 OAuth(무이메일) + saved_trips. 카카오 HTTP는 KakaoGateway 목 —
 * 판정·저장은 실 DB로 왕복한다 (verdict_at_save는 실제 judge 산출).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthTripsTest {
  static final String OAUTH_ID = "test-oauth-authtrips";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;
  @MockitoBean KakaoGateway kakao;

  @AfterAll
  void cleanup() {
    // saved_trips는 ON DELETE CASCADE로 함께 정리
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id = ?", OAUTH_ID);
  }

  @Test
  void 비로그인_사용자평면_전부_401() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/saved-trips")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/saved-trips")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/saved-trips/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void 위조_쿠키_401() throws Exception {
    mvc.perform(get("/api/me").cookie(new Cookie("gj_session", "1.9999999999.deadbeef")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 위조_Bearer_401() throws Exception {
    mvc.perform(get("/api/me").header("Authorization", "Bearer 1.9999999999.deadbeef"))
        .andExpect(status().isUnauthorized());
  }

  /**
   * 프론트(vercel.app)와 API(api.geojero.com)가 다른 사이트인 동안 SameSite=Lax 쿠키는
   * 브라우저에 저장되지 않는다. 같은 토큰을 Authorization: Bearer로 보내도 통해야 한다 —
   * 이게 없으면 로그인이 200을 받고도 /api/me가 401이 되어 "로그인했는데 안 된" 상태가 된다.
   */
  @Test
  void Bearer_토큰으로_사용자평면_왕복() throws Exception {
    when(kakao.exchange("bearer-code", "http://localhost/cb"))
        .thenReturn(new KakaoGateway.KakaoUser(OAUTH_ID, "테스트유저"));
    var login = mvc.perform(post("/api/auth/kakao").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"bearer-code\",\"redirectUri\":\"http://localhost/cb\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andReturn();
    String token = new ObjectMapper()
        .readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .path("token").asText();

    // 쿠키를 아예 붙이지 않는다 — 교차 사이트 브라우저와 같은 조건
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("테스트유저"));
    mvc.perform(get("/api/saved-trips").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  /** REST 키가 없으면 로그인 시작만 503이고 판정·조회는 계속 돈다. */
  @Test
  void 카카오_미설정이면_start만_503() throws Exception {
    when(kakao.authorizeUrl(anyString())).thenReturn(null);
    mvc.perform(get("/api/auth/kakao/start?redirectUri=http://localhost/cb"))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/pois")).andExpect(status().isOk());
  }

  @Test
  void 카카오_설정되면_start가_kauth로_302() throws Exception {
    when(kakao.authorizeUrl("http://localhost/cb"))
        .thenReturn("https://kauth.kakao.com/oauth/authorize?response_type=code"
            + "&client_id=KEY&redirect_uri=http%3A%2F%2Flocalhost%2Fcb");
    mvc.perform(get("/api/auth/kakao/start?redirectUri=http://localhost/cb"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location",
            Matchers.startsWith("https://kauth.kakao.com/oauth/authorize")));
  }

  @Test
  void 카카오_장애시_로그인만_502_판정은_정상() throws Exception {
    when(kakao.exchange(anyString(), anyString()))
        .thenThrow(new IllegalStateException("kauth HTTP 500"));
    mvc.perform(post("/api/auth/kakao").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"down\",\"redirectUri\":\"http://localhost/cb\"}"))
        .andExpect(status().isBadGateway());
    mvc.perform(post("/api/judge").contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"date":"2026-09-09","startTime":"06:50","legs":[
              {"type":"BUS","from":"고현","to":"해금강"}]}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feasible").value("YES"));
  }

  @Test
  void 로그인_저장_목록_삭제_왕복() throws Exception {
    when(kakao.exchange("mock-code", "http://localhost/cb"))
        .thenReturn(new KakaoGateway.KakaoUser(OAUTH_ID, "테스트유저"));
    var login = mvc.perform(post("/api/auth/kakao").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"mock-code\",\"redirectUri\":\"http://localhost/cb\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.nickname").value("테스트유저"))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("HttpOnly")))
        .andReturn();
    var cookie = login.getResponse().getCookie("gj_session");
    assertNotNull(cookie);

    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("테스트유저"));

    // 저장 직전 판정 실행 → verdict_at_save (부산발 당일치기 — 실 DB 판정 YES)
    var save = mvc.perform(post("/api/saved-trips").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"courseId":1,"travelDate":"2026-09-09",
                 "arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.verdictAtSave.feasible").value("YES"))
        .andExpect(jsonPath("$.verdictAtSave.dayClass").value("WEEKDAY"))
        .andReturn();
    long id = new ObjectMapper()
        .readTree(save.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .path("savedTripId").asLong();

    mvc.perform(get("/api/saved-trips").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].savedTripId").value(id))
        .andExpect(jsonPath("$[0].verdictAtSave.feasible").value("YES"))
        // 재판정(verdictNow): 저장 이후 데이터가 안 변했으므로 같은 답
        .andExpect(jsonPath("$[0].verdictNow.feasible").value("YES"));

    mvc.perform(delete("/api/saved-trips/" + id).cookie(cookie))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/saved-trips/" + id).cookie(cookie))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/saved-trips").cookie(cookie))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 저장_알수없는_코스_400() throws Exception {
    Long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, OAUTH_ID);
    var cookie = new Cookie("gj_session", sessions.issue(uid).getValue());
    mvc.perform(post("/api/saved-trips").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"courseId":99,"travelDate":"2026-09-09",
             "arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isBadRequest());
  }

  /**
   * V10 — 사용자가 스팟을 골라 조립한 코스도 저장된다.
   *
   * 이전에는 course_id NOT NULL이라 검증 코스 3종만 저장됐고, 화면이 만든 코스에는
   * 붙일 이름표가 없어 저장 버튼이 막혀 있었다. 이 케이스가 그 회귀를 지킨다.
   */
  @Test
  void 고른_스팟으로_조립한_코스_저장_목록_삭제() throws Exception {
    Long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, OAUTH_ID);
    var cookie = new Cookie("gj_session", sessions.issue(uid).getValue());

    var save = mvc.perform(post("/api/saved-trips").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"legs":[{"type":"BUS","from":"고현","to":"학동"},
                         {"type":"BUS","from":"학동","to":"고현"}],
                 "title":"부산서부 → 거제 · 학동",
                 "chain":"부산서부 → 고현 → 학동 → 고현 → 부산서부",
                 "travelDate":"2026-09-09","arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseId").doesNotExist())
        .andExpect(jsonPath("$.title").value("부산서부 → 거제 · 학동"))
        .andExpect(jsonPath("$.verdictAtSave.dayClass").value("WEEKDAY"))
        .andReturn();
    long id = new ObjectMapper()
        .readTree(save.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .path("savedTripId").asLong();

    // 목록에서 재판정이 돈다 — legs를 저장했기 때문에 오늘 기준으로 다시 계산할 수 있다.
    mvc.perform(get("/api/saved-trips").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].savedTripId").value(id))
        .andExpect(jsonPath("$[0].chain").value("부산서부 → 고현 → 학동 → 고현 → 부산서부"))
        .andExpect(jsonPath("$[0].verdictNow.feasible").isString());

    mvc.perform(delete("/api/saved-trips/" + id).cookie(cookie))
        .andExpect(status().isNoContent());
  }

  /** courseId와 legs는 정확히 하나여야 한다 — 둘 다 없으면 판정할 근거가 없다. */
  @Test
  void 저장_courseId도_legs도_없으면_400() throws Exception {
    Long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, OAUTH_ID);
    var cookie = new Cookie("gj_session", sessions.issue(uid).getValue());
    mvc.perform(post("/api/saved-trips").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"travelDate":"2026-09-09","arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isBadRequest());
  }

  /** 모르는 정류소가 섞이면 저장을 만들지 않는다 — 재판정이 영원히 실패하는 행을 남기지 않는다. */
  @Test
  void 저장_판정불가_구간이면_400() throws Exception {
    Long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, OAUTH_ID);
    var cookie = new Cookie("gj_session", sessions.issue(uid).getValue());
    mvc.perform(post("/api/saved-trips").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"legs":[{"type":"FIXED","endStop":"고현","endTime":"없는시각"}],
             "travelDate":"2026-09-09","arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isBadRequest());
  }
}
