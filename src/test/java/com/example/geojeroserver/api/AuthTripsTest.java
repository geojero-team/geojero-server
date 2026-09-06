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
}
