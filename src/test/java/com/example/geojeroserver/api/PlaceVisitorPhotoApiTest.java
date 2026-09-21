package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.photos.TestImages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 맛집 · 숙소 · 카페의 방문자 사진(V51, 2026-09-22 사용자: *"거제도 스팟처럼 아래 후기 올릴 수 있는 기능도 넣고 싶거든"*).
 *
 * 스팟(VisitorPhotoApiTest)과 **같은 표 · 같은 길**이다 — 여기서는 그 사실과 장소 쪽 입구만 지킨다:
 * 보기는 비로그인 · 올리기는 로그인 · 없는 곳은 404 · 사진 하나에 id 하나라 보기 · 삭제 주소가 스팟과 같다.
 * 위치정보 지우기 · 형식 거부 같은 사진 처리 자체는 스팟 테스트가 이미 지킨다(같은 코드를 탄다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceVisitorPhotoApiTest {
  static final String TEST_USERS = "test-oauth-placephotos-";
  static final String ALICE = TEST_USERS + "alice";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;

  @BeforeEach
  void reset() {
    jdbc.update("""
        DELETE FROM visitor_photos WHERE user_id IN
          (SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?)""",
        TEST_USERS + "%");
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?", TEST_USERS + "%");
  }

  /** 처음엔 0장이고 그것이 정상이다(404 가 아니다) — 스팟과 같은 규칙. */
  @Test void 사진이_없으면_빈_목록이고_비로그인으로_보인다() throws Exception {
    long id = place("CAFE");
    mvc.perform(get("/api/places/{id}/visitor-photos", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.placeId").value((int) id))
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.photos.length()").value(0));
  }

  @Test void 토큰_없이_올리면_401() throws Exception {
    mvc.perform(multipart("/api/places/{id}/visitor-photos", place("FOOD"))
            .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", TestImages.jpeg(200, 100))))
        .andExpect(status().isUnauthorized());
  }

  @Test void 없는_곳에_올리거나_목록을_부르면_404() throws Exception {
    mvc.perform(get("/api/places/{id}/visitor-photos", 999_999_999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    upload(token(ALICE), 999_999_999L, TestImages.jpeg(200, 100))
        .andExpect(status().isNotFound());
  }

  /**
   * 올리면 201 이고 곧바로 목록에 뜬다. 사진 주소 · 삭제 주소는 **스팟과 같은 것**이다
   * (`/api/visitor-photos/{id}/...`) — 표를 하나로 둔 이유가 이것이다.
   */
  @Test void 올리면_목록에_뜨고_보기와_삭제는_스팟과_같은_주소다() throws Exception {
    long id = place("STAY");
    String token = token(ALICE);

    var res = upload(token, id, TestImages.jpeg(200, 100))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isMine").value(true))
        .andReturn();
    long photoId = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.photoId") instanceof Number n
        ? n.longValue() : -1;
    assertTrue(photoId > 0);

    mvc.perform(get("/api/places/{id}/visitor-photos", id))
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.photos[0].photoId").value((int) photoId))
        .andExpect(jsonPath("$.photos[0].imageUrl").value("/api/visitor-photos/" + photoId + "/image"))
        .andExpect(jsonPath("$.photos[0].isMine").value(false)); // 비로그인으로 보면 남의 사진이다

    mvc.perform(get("/api/visitor-photos/{id}/image", photoId))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/visitor-photos/{id}", photoId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/places/{id}/visitor-photos", id)).andExpect(jsonPath("$.count").value(0));
  }

  /** 스팟 사진과 섞이지 않는다 — 같은 표를 쓰되 둘 중 하나만 가리킨다(chk_visitor_photos_one_target). */
  @Test void 장소_사진은_스팟_목록에_섞이지_않는다() throws Exception {
    long id = place("FOOD");
    upload(token(ALICE), id, TestImages.jpeg(200, 100)).andExpect(status().isCreated());
    long poi = jdbc.queryForObject(
        "SELECT poi_id FROM pois WHERE theme IS NOT NULL ORDER BY poi_id LIMIT 1", Long.class);
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(jsonPath("$.photos[?(@.isMine == true)]").isEmpty());
    assertEquals(1, (int) jdbc.queryForObject(
        "SELECT count(*) FROM visitor_photos WHERE place_id = ? AND poi_id IS NULL", Integer.class, id));
  }

  // ── 거들 ────────────────────────────────────────────────────────────────

  private long place(String kind) {
    return jdbc.queryForObject(
        "SELECT content_id FROM places WHERE kind = ? ORDER BY sort_order LIMIT 1", Long.class, kind);
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

  private ResultActions upload(String token, long placeId, byte[] bytes) throws Exception {
    return mvc.perform(multipart("/api/places/{id}/visitor-photos", placeId)
        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", bytes))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }
}
