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
  void 카카오_장애시_로그인만_502_시간표조회는_정상() throws Exception {
    // CLAUDE.md: "인증·저장·TourAPI 장애가 판정을 막지 않는다" — 판정이 빠진 뒤에도
    // 같은 불변식이 유지돼야 한다. 비로그인으로 도는 것이 시간표·이동시간 조회다.
    when(kakao.exchange(anyString(), anyString()))
        .thenThrow(new IllegalStateException("kauth HTTP 500"));
    mvc.perform(post("/api/auth/kakao").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"down\",\"redirectUri\":\"http://localhost/cb\"}"))
        .andExpect(status().isBadGateway());
    mvc.perform(get("/api/stops/고현/departures?to=해금강&date=2026-09-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastDeparture").value("19:15"));
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

    // 저장은 코스를 가리킨다. 판정 제거로 verdictAtSave·verdictNow가 응답에서 빠졌고,
    // 코스 이름·요약(title·chain)은 서버가 코스 상수에서 채운다.
    var save = mvc.perform(post("/api/saved-trips").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"courseId":1,"travelDate":"2026-09-09",
                 "arrivalTime":"08:20","returnTime":"21:10"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseId").value(1))
        .andExpect(jsonPath("$.title").value("부산발 당일치기"))
        .andExpect(jsonPath("$.verdictAtSave").doesNotExist())
        .andReturn();
    long id = new ObjectMapper()
        .readTree(save.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .path("savedTripId").asLong();

    mvc.perform(get("/api/saved-trips").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].savedTripId").value(id))
        .andExpect(jsonPath("$[0].title").value("부산발 당일치기"))
        .andExpect(jsonPath("$[0].travelDate").value("2026-09-09"));

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
   * courseId가 없으면 저장할 대상이 없다.
   *
   * 전에는 "courseId 또는 legs 중 정확히 하나"였다. legs는 **사용자가 스팟을 골라 조립한
   * 코스**를 판정해 저장하려던 경로였고(V10), 판정 제거와 함께 빠졌다 — 이제 코스는
   * 우리가 짜서 내려준다. 컬럼은 남겼다(V15 주석).
   */
  @Test
  void 저장_courseId가_없으면_400() throws Exception {
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

  /** 날짜·시각 형식이 깨지면 저장을 만들지 않는다 — 읽을 수 없는 행을 남기지 않는다. */
  @Test
  void 저장_날짜형식이_깨지면_400() throws Exception {
    Long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, OAUTH_ID);
    var cookie = new Cookie("gj_session", sessions.issue(uid).getValue());
    mvc.perform(post("/api/saved-trips").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"courseId":1,"travelDate":"2026-09-09",
             "arrivalTime":"없는시각","returnTime":"21:10"}"""))
        .andExpect(status().isBadRequest());
  }
}
