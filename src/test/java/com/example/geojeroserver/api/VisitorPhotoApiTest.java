package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.photos.TestImages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 방문자 사진 API — 로컬 Postgres 로 왕복한다.
 *
 * TourAPI 사진과 섞지 않는다. 별도 테이블(visitor_photos · visitor_photo_blobs)·별도 경로다.
 * 로그인은 users 행을 넣고 SessionCookies 로 토큰을 만들어 흉내 낸다(AuthTripsTest 와 같은 방식).
 * poi_id 는 하드코딩하지 않는다 — 14~22 는 V14 VALUES 순서와 다르게 배정됐다.
 *
 * DB 세션 시간대를 UTC 로 고정한다. PgJDBC 는 JVM 기본 시간대를 세션에 싣는데 이 PC 도 운영 이미지
 * (Dockerfile ENV TZ=Asia/Seoul)도 KST 라, 날짜를 그냥 ::date 로 잘라도 통과해 버려 실수가 드러나지 않는다.
 * 날짜가 세션·JVM 시간대에 기대지 않는다는 것을 여기서 지킨다.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisitorPhotoApiTest {
  /** 이 접두사로 만든 계정만 지운다. */
  static final String TEST_USERS = "test-oauth-visitorphotos-";
  static final String OWNER = TEST_USERS + "owner";
  static final String OTHER = TEST_USERS + "other";
  static final String GONE = TEST_USERS + "gone";
  /** 테스트와 무관한 남. 이 계정의 사진은 "로컬 DB 에 이미 있던 사진" 역할이다. */
  static final String BYSTANDER = TEST_USERS + "bystander";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;

  @BeforeEach
  void resetPhotos() {
    // 바이트(visitor_photo_blobs)는 ON DELETE CASCADE 로 함께 지워진다
    jdbc.update("""
        DELETE FROM visitor_photos WHERE user_id IN
          (SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?)""",
        TEST_USERS + "%");
    // 로컬 DB 는 공유다 — 클라 확인이 같은 DB 에 사진을 올린다. 남의 사진이 있어도 통과해야 하므로
    // 첫 화면 스팟에 남의 사진 한 장을 늘 깔고 시작한다(비우고 난 뒤여야 한다).
    jdbc.update("""
        WITH p AS (
          INSERT INTO visitor_photos (poi_id, user_id, width, height, byte_size)
          VALUES ((SELECT poi_id FROM pois WHERE theme IS NOT NULL ORDER BY poi_id LIMIT 1), ?, 1, 1, 1)
          RETURNING photo_id
        )
        INSERT INTO visitor_photo_blobs (photo_id, jpeg) SELECT photo_id, ? FROM p""",
        userId(BYSTANDER), new byte[] {1});
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id LIKE ?",
        TEST_USERS + "%");
  }

  // ── 스키마 ─────────────────────────────────────────────────────────────────

  /** 계정을 지우면 사진 행과 바이트가 함께 사라진다. 우리가 남의 사진을 계속 들고 있지 않는다. */
  @Test void 계정을_지우면_사진과_바이트가_함께_지워진다() {
    long uid = userId(GONE);
    long photoId = jdbc.queryForObject("""
        INSERT INTO visitor_photos (poi_id, user_id, caption, width, height, byte_size)
        VALUES (?, ?, NULL, 1, 1, 1) RETURNING photo_id""", Long.class, spotPoi(), uid);
    jdbc.update("INSERT INTO visitor_photo_blobs (photo_id, jpeg) VALUES (?, ?)",
        photoId, new byte[] {1});

    jdbc.update("DELETE FROM users WHERE user_id = ?", uid);

    assertEquals(0, count("visitor_photos", photoId));
    assertEquals(0, count("visitor_photo_blobs", photoId));
  }

  @Test void 치수와_크기는_0보다_커야_한다() {
    long uid = userId(OWNER);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
        INSERT INTO visitor_photos (poi_id, user_id, width, height, byte_size)
        VALUES (?, ?, 0, 1, 1)""", spotPoi(), uid));
  }

  // ── 목록: 비로그인 ───────────────────────────────────────────────────────

  // ── 신고(V33) ─────────────────────────────────────────────────────────────

  /**
   * 신고 한 건이면 목록과 이미지 경로 **양쪽에서** 사라진다. 목록에서만 빼면 주소를 아는 사람에게는
   * 계속 보인다. 행은 남긴다 — 잘못된 신고를 되돌릴 수 있어야 한다(hidden_at 을 NULL 로).
   */
  @Test void 신고하면_목록과_이미지에서_사라지고_행은_남는다() throws Exception {
    long poi = spotPoi();
    long photoId = jdbc.queryForObject("""
        INSERT INTO visitor_photos (poi_id, user_id, caption, width, height, byte_size)
        VALUES (?, ?, NULL, 1, 1, 1) RETURNING photo_id""", Long.class, poi, userId(OWNER));
    jdbc.update("INSERT INTO visitor_photo_blobs (photo_id, jpeg) VALUES (?, ?)",
        photoId, new byte[] {1});
    mvc.perform(get("/api/pois/" + poi + "/visitor-photos"))
        .andExpect(jsonPath("$.count").value(1));

    mvc.perform(post("/api/visitor-photos/" + photoId + "/report")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OTHER)))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/pois/" + poi + "/visitor-photos"))
        .andExpect(jsonPath("$.count").value(0));
    mvc.perform(get("/api/visitor-photos/" + photoId + "/image"))
        .andExpect(status().isNotFound());
    assertEquals(1, count("visitor_photos", photoId));
  }

  /** 같은 사람이 두 번 눌러도 결과가 같다(204). 신고 기록은 한 줄이다 — (photo_id, user_id) 가 기본키다. */
  @Test void 같은_사람이_두_번_신고해도_204이고_기록은_하나() throws Exception {
    long photoId = jdbc.queryForObject("""
        INSERT INTO visitor_photos (poi_id, user_id, width, height, byte_size)
        VALUES (?, ?, 1, 1, 1) RETURNING photo_id""", Long.class, spotPoi(), userId(OWNER));
    String token = token(OTHER);

    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/visitor-photos/" + photoId + "/report")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isNoContent());
    }
    assertEquals(1, (int) jdbc.queryForObject(
        "SELECT count(*) FROM visitor_photo_reports WHERE photo_id = ?", Integer.class, photoId));
  }

  /** 비로그인 신고는 401. 누가 신고했는지 남지 않으면 장난 신고를 되짚을 수 없다. */
  @Test void 로그인_없이_신고는_401_없는_사진은_404() throws Exception {
    long photoId = jdbc.queryForObject("""
        INSERT INTO visitor_photos (poi_id, user_id, width, height, byte_size)
        VALUES (?, ?, 1, 1, 1) RETURNING photo_id""", Long.class, spotPoi(), userId(OWNER));

    mvc.perform(post("/api/visitor-photos/" + photoId + "/report"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/visitor-photos/99999999/report")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OTHER)))
        .andExpect(status().isNotFound());
    // 401·404 는 아무것도 감추지 않는다
    assertEquals(0, (int) jdbc.queryForObject(
        "SELECT count(*) FROM visitor_photos WHERE photo_id = ? AND hidden_at IS NOT NULL",
        Integer.class, photoId));
  }

  // ── 목록: 비로그인 ───────────────────────────────────────────────────────

  /** 처음엔 17곳 전부 0장이다. 0장은 404 가 아니라 정상 상태다 — 화면은 빈 상태를 그린다. */
  @Test void 사진이_없는_스팟은_200_0장() throws Exception {
    long poi = spotPoi();
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poiId").value((int) poi))
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.photos").isArray())
        .andExpect(jsonPath("$.photos.length()").value(0));
  }

  /** 없는 poi 와 화면에 나오지 않는 poi(theme NULL — 명사해수욕장 등)는 사진 칸이 없다. */
  @Test void 없는_poi와_화면_스팟이_아닌_poi는_404() throws Exception {
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", missingPoi()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POI_NOT_FOUND"));
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", nonSpotPoi()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POI_NOT_FOUND"));
  }

  /** 고현터미널(V22)은 스팟이 아니지만 홈 지도에서 스팟처럼 펼쳐지고 방문자 사진 칸이 있다(2026-09-13 사용자 결정). */
  @Test void 고현터미널은_사진_칸이_있다_목록_200_올리기_201() throws Exception {
    long terminal = jdbc.queryForObject("SELECT poi_id FROM pois WHERE poi_kind = 'TERMINAL'", Long.class);
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", terminal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poiId").value((int) terminal));

    upload(token(OWNER), terminal, TestImages.jpeg(40, 30), "터미널 앞")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.caption").value("터미널 앞"));
  }

  // ── 인증: Spring Security 가 permitAll 이라 컨트롤러가 막는다 ─────────────────

  /** 쓰기 핸들러에서 require() 한 줄만 빠져도 로그인 없는 쓰기가 열린다. 엔드포인트마다 지킨다. */
  @Test void 토큰_없이_올리기와_지우기는_401() throws Exception {
    upload(null, spotPoi(), TestImages.jpeg(40, 30), null)
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/visitor-photos/{photoId}", 1))
        .andExpect(status().isUnauthorized());
  }

  @Test void 위조_토큰으로_올리기와_지우기는_401() throws Exception {
    upload("1.9999999999.deadbeef", spotPoi(), TestImages.jpeg(40, 30), null)
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/visitor-photos/{photoId}", 1)
            .header(HttpHeaders.AUTHORIZATION, "Bearer 1.9999999999.deadbeef"))
        .andExpect(status().isUnauthorized());
  }

  /**
   * 토큰 서명은 users 행을 보지 않는다(7일 유효). 계정을 지운 뒤의 토큰으로 올리면
   * FK 위반 500 이 아니라 401 이어야 한다.
   */
  @Test void 삭제된_계정의_토큰으로_올리면_401() throws Exception {
    long uid = userId(GONE);
    String token = sessions.issueToken(uid);
    jdbc.update("DELETE FROM users WHERE user_id = ?", uid);

    upload(token, spotPoi(), TestImages.jpeg(40, 30), null)
        .andExpect(status().isUnauthorized());
    assertEquals(0, jdbc.queryForObject(
        "SELECT count(*) FROM visitor_photos WHERE user_id = ?", Integer.class, uid));
  }

  // ── 올리기 ─────────────────────────────────────────────────────────────────

  /**
   * ★ "사진 속 위치 정보는 저장하지 않아요"(268:529)는 **저장된 바이트**로 확인한다.
   * 응답이나 이미지 GET 만 보면 "원본을 저장하고 내려줄 때만 지우는" 구현도 통과한다.
   */
  @Test void GPS가_든_JPEG를_올리면_201_저장된_바이트에_위치정보가_없다() throws Exception {
    byte[] input = TestImages.jpegWithExif(200, 100, 1, false, true);
    assertTrue(TestImages.containsAscii(input, TestImages.FAKE_GPS_MARK)); // 픽스처 확인
    long poi = spotPoi();

    var res = upload(token(OWNER), poi, input, "몽돌 소리가 좋았다")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.width").value(200))
        .andExpect(jsonPath("$.height").value(100))
        .andExpect(jsonPath("$.caption").value("몽돌 소리가 좋았다"))
        .andExpect(jsonPath("$.isMine").value(true))
        .andExpect(jsonPath("$.uploadedDate")
            .value(LocalDate.now(ZoneId.of("Asia/Seoul")).toString()))
        .andReturn();
    JsonNode body = json(res);
    long photoId = body.path("photoId").asLong();
    assertEquals("/api/visitor-photos/" + photoId + "/image", body.path("imageUrl").asText());
    // 작성자 닉네임·userId·바이트는 내려주지 않는다
    assertEquals(PHOTO_KEYS, fieldNames(body));

    byte[] stored = jdbc.queryForObject(
        "SELECT jpeg FROM visitor_photo_blobs WHERE photo_id = ?", byte[].class, photoId);
    assertEquals(0xFF, stored[0] & 0xFF);
    assertEquals(0xD8, stored[1] & 0xFF);
    assertEquals(0, TestImages.metadataSegments(stored),
        "APPn/COM: " + TestImages.headerMarkers(stored));
    assertFalse(TestImages.containsAscii(stored, TestImages.FAKE_GPS_MARK));
    assertFalse(TestImages.containsAscii(stored, "Exif"));

    var row = jdbc.queryForMap(
        "SELECT poi_id, user_id, byte_size FROM visitor_photos WHERE photo_id = ?", photoId);
    assertEquals(poi, ((Number) row.get("poi_id")).longValue());
    assertEquals(userId(OWNER), ((Number) row.get("user_id")).longValue());
    assertEquals(stored.length, ((Number) row.get("byte_size")).intValue());
  }

  /** 세로로 찍은 폰 사진(Orientation 6)은 세워서 저장한다. 치수도 세운 값이다. */
  @Test void Orientation_6이면_가로세로가_뒤집혀_저장된다() throws Exception {
    var res = upload(token(OWNER), spotPoi(), TestImages.jpegWithExif(200, 100, 6, true, true), null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.width").value(100))
        .andExpect(jsonPath("$.height").value(200))
        .andReturn();
    long photoId = json(res).path("photoId").asLong();
    var row = jdbc.queryForMap("SELECT width, height FROM visitor_photos WHERE photo_id = ?",
        photoId);
    assertEquals(100, row.get("width"));
    assertEquals(200, row.get("height"));
    byte[] stored = jdbc.queryForObject(
        "SELECT jpeg FROM visitor_photo_blobs WHERE photo_id = ?", byte[].class, photoId);
    var img = TestImages.decode(stored);
    assertEquals(100, img.getWidth());
    assertEquals(200, img.getHeight());
  }

  /** HEIC 는 JDK 가 못 읽는다. 파트의 Content-Type 이 image/jpeg 여도 바이트로 가른다. 행을 남기지 않는다. */
  @Test void HEIC는_415_행이_남지_않는다() throws Exception {
    upload(token(OWNER), spotPoi(), TestImages.heic(), null)
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
    assertEquals(0, testPhotoRows());
  }

  @Test void 읽을_수_없는_사진과_픽셀_초과는_400_행이_남지_않는다() throws Exception {
    byte[] truncated = java.util.Arrays.copyOf(TestImages.jpeg(200, 100), 40);
    upload(token(OWNER), spotPoi(), truncated, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IMAGE_UNREADABLE"));
    upload(token(OWNER), spotPoi(), TestImages.pngHeaderClaiming(60000, 60000), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IMAGE_TOO_MANY_PIXELS"));
    assertEquals(0, testPhotoRows());
  }

  @Test void 파일이_없으면_400() throws Exception {
    mvc.perform(multipart("/api/pois/{poiId}/visitor-photos", spotPoi())
            .param("caption", "사진 없이")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OWNER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    upload(token(OWNER), spotPoi(), new byte[0], null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    assertEquals(0, testPhotoRows());
  }

  @Test void 올리기도_없는_poi와_화면_스팟이_아닌_poi는_404() throws Exception {
    upload(token(OWNER), missingPoi(), TestImages.jpeg(40, 30), null)
        .andExpect(status().isNotFound());
    upload(token(OWNER), nonSpotPoi(), TestImages.jpeg(40, 30), null)
        .andExpect(status().isNotFound());
    assertEquals(0, testPhotoRows());
  }

  // ── 캡션: 선택 · 앞뒤 공백 제거 · 200자(코드포인트) ─────────────────────────

  /**
   * 200자는 **코드포인트**로 센다. 이모지는 String.length() 로 2라서 length 로 세면
   * 사용자가 200자를 썼는데 거부된다. 경계 양쪽을 함께 본다.
   */
  @Test void 캡션은_코드포인트_200자까지_201_넘으면_400() throws Exception {
    String exactly200 = "😀".repeat(100) + "가".repeat(100);
    assertEquals(200, exactly200.codePointCount(0, exactly200.length()));
    assertEquals(300, exactly200.length()); // UTF-16 단위로는 넘는다

    upload(token(OWNER), spotPoi(), TestImages.jpeg(40, 30), exactly200 + "가")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPTION_TOO_LONG"));
    assertEquals(0, testPhotoRows());

    var res = upload(token(OWNER), spotPoi(), TestImages.jpeg(40, 30), exactly200)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.caption").value(exactly200))
        .andReturn();
    assertEquals(exactly200, jdbc.queryForObject(
        "SELECT caption FROM visitor_photos WHERE photo_id = ?", String.class,
        json(res).path("photoId").asLong()));
  }

  /** 공백만 쓴 캡션은 없는 캡션이다. 화면이 빈 캡션 카드를 그리지 않게 null 로 둔다. */
  @Test void 캡션은_앞뒤_공백을_지우고_비면_null() throws Exception {
    var blank = upload(token(OWNER), spotPoi(), TestImages.jpeg(40, 30), " \t\n ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.caption").value(org.hamcrest.Matchers.nullValue()))
        .andReturn();
    assertNull(jdbc.queryForObject("SELECT caption FROM visitor_photos WHERE photo_id = ?",
        String.class, json(blank).path("photoId").asLong()));

    upload(token(OWNER), spotPoi(), TestImages.jpeg(40, 30), "  몽돌 소리  ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.caption").value("몽돌 소리"));
  }

  // ── 이미지 · 삭제 ──────────────────────────────────────────────────────────

  /** 이미지는 비로그인으로 받는다. 내려주는 바이트는 저장된 재인코딩 JPEG 그대로다. */
  @Test void 이미지는_비로그인_200_image_jpeg() throws Exception {
    long photoId = json(upload(token(OWNER), spotPoi(),
        TestImages.jpegWithExif(200, 100, 1, false, true), null)
        .andExpect(status().isCreated()).andReturn()).path("photoId").asLong();
    byte[] stored = jdbc.queryForObject(
        "SELECT jpeg FROM visitor_photo_blobs WHERE photo_id = ?", byte[].class, photoId);

    var res = mvc.perform(get("/api/visitor-photos/{photoId}/image", photoId))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        // 캐시하지 않는다 — 지운 사진이 브라우저 캐시에 남아 계속 보이지 않게(Spring Security 기본 헤더)
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andReturn();
    assertArrayEquals(stored, res.getResponse().getContentAsByteArray());

    mvc.perform(get("/api/visitor-photos/{photoId}/image", missingPhoto()))
        .andExpect(status().isNotFound());
  }

  /** 본인 사진만 지운다. 지우면 행·바이트가 사라지고 이미지도 404 다. */
  @Test void 본인은_지우고_남은_403_없는_사진은_404() throws Exception {
    long poi = spotPoi();
    long photoId = json(upload(token(OWNER), poi, TestImages.jpeg(40, 30), null)
        .andExpect(status().isCreated()).andReturn()).path("photoId").asLong();

    // 방문자 사진은 목록으로 이미 공개돼 있어 존재를 숨길 이유가 없다 — 남의 것은 403 으로 갈라 알린다
    mvc.perform(delete("/api/visitor-photos/{photoId}", photoId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OTHER)))
        .andExpect(status().isForbidden());
    assertEquals(1, count("visitor_photos", photoId));

    mvc.perform(delete("/api/visitor-photos/{photoId}", photoId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OWNER)))
        .andExpect(status().isNoContent());
    assertEquals(0, count("visitor_photos", photoId));
    assertEquals(0, count("visitor_photo_blobs", photoId));
    mvc.perform(get("/api/visitor-photos/{photoId}/image", photoId))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(jsonPath("$.count").value(0));

    mvc.perform(delete("/api/visitor-photos/{photoId}", photoId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OWNER)))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/visitor-photos/{photoId}", missingPhoto())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OWNER)))
        .andExpect(status().isNotFound());
  }

  // ── 목록: isMine ──────────────────────────────────────────────────────────

  /** isMine 은 토큰으로만 갈린다. 토큰이 깨졌거나 만료됐어도 보기는 200 이다(require() 금지). */
  @Test void 목록의_isMine은_토큰으로_갈리고_깨진_토큰도_200() throws Exception {
    long poi = spotPoi();
    long photoId = json(upload(token(OWNER), poi, TestImages.jpeg(40, 30), null)
        .andExpect(status().isCreated()).andReturn()).path("photoId").asLong();
    long owner = userId(OWNER);
    long now = Instant.now().getEpochSecond();

    var mine = mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.photos[0].photoId").value((int) photoId))
        .andExpect(jsonPath("$.photos[0].isMine").value(true))
        .andReturn();
    JsonNode item = json(mine).path("photos").get(0);
    assertEquals(PHOTO_KEYS, fieldNames(item));
    assertTrue(item.path("caption").isNull()); // 캡션이 없어도 필드는 있다

    // 이 방식으로 서명한 토큰이 통한다는 것을 먼저 보인다 — 아래 만료 토큰이 서명 탓에 떨어지는 게 아니다
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken(owner, now + 60)))
        .andExpect(jsonPath("$.photos[0].isMine").value(true));

    for (String bad : new String[] {null, "Bearer " + token(OTHER), "Bearer 1.9999999999.deadbeef",
        "Bearer " + signedToken(owner, now - 60)}) {
      var req = get("/api/pois/{poiId}/visitor-photos", poi);
      if (bad != null) {
        req.header(HttpHeaders.AUTHORIZATION, bad);
      }
      mvc.perform(req)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.count").value(1))
          .andExpect(jsonPath("$.photos[0].isMine").value(false));
    }
  }

  // ── 목록: 순서 · 날짜 ─────────────────────────────────────────────────────

  /**
   * 최신순(created_at DESC, photo_id DESC). 날짜는 **KST 로 올린 날**이다 — 세션이 UTC 일 때
   * 그냥 ::date 로 자르면 KST 00~09시 업로드가 전날로 찍힌다. 경계 양쪽 시각을 직접 넣어 본다.
   */
  @Test void 목록은_최신순이고_날짜는_KST로_올린_날() throws Exception {
    long poi = spotPoi();
    long first = json(upload(token(OWNER), poi, TestImages.jpeg(40, 30), null)
        .andExpect(status().isCreated()).andReturn()).path("photoId").asLong();
    long second = json(upload(token(OTHER), poi, TestImages.jpeg(40, 30), null)
        .andExpect(status().isCreated()).andReturn()).path("photoId").asLong();

    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(jsonPath("$.count").value(2))
        .andExpect(jsonPath("$.photos[0].photoId").value((int) second))
        .andExpect(jsonPath("$.photos[1].photoId").value((int) first));

    // 먼저 올린 사진을 더 늦은 시각으로 옮기면 순서가 뒤집힌다 — id 가 아니라 시각이 기준이다
    setCreatedAt(first, "2026-09-12 15:30:00+00");  // KST 2026-09-13 00:30
    setCreatedAt(second, "2026-09-12 14:59:59+00"); // KST 2026-09-12 23:59
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(jsonPath("$.photos[0].photoId").value((int) first))
        .andExpect(jsonPath("$.photos[0].uploadedDate").value("2026-09-13"))
        .andExpect(jsonPath("$.photos[1].photoId").value((int) second))
        .andExpect(jsonPath("$.photos[1].uploadedDate").value("2026-09-12"));

    // 같은 시각이면 나중 id 가 앞이다 — 순서가 실행마다 흔들리지 않게 못박는다
    setCreatedAt(second, "2026-09-12 15:30:00+00");
    mvc.perform(get("/api/pois/{poiId}/visitor-photos", poi))
        .andExpect(jsonPath("$.photos[0].photoId").value((int) second))
        .andExpect(jsonPath("$.photos[1].photoId").value((int) first));
  }

  // ── 준비 ──────────────────────────────────────────────────────────────────

  private void setCreatedAt(long photoId, String timestamptz) {
    jdbc.update("UPDATE visitor_photos SET created_at = ?::timestamptz WHERE photo_id = ?",
        timestamptz, photoId);
  }

  static final Set<String> PHOTO_KEYS =
      Set.of("photoId", "imageUrl", "width", "height", "caption", "uploadedDate", "isMine");

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

  ResultActions upload(String token, long poiId, byte[] bytes, String caption) throws Exception {
    // 파트의 Content-Type 은 일부러 늘 image/jpeg 로 보낸다 — 서버는 바이트로 가른다.
    var req = multipart("/api/pois/{poiId}/visitor-photos", poiId)
        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes));
    if (caption != null) {
      req.param("caption", caption);
    }
    if (token != null) {
      req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mvc.perform(req);
  }

  long userId(String oauthId) {
    return jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, oauthId);
  }

  /**
   * 화면에 나오는 스팟(theme IS NOT NULL) 중 **지금 사진이 한 장도 없는** 곳. 개수를 단언하는 테스트가
   * 로컬 DB 에 있던 남의 사진에 흔들리지 않게 한다. 호출 시점 기준이라 같은 스팟이 필요하면 한 번 받아 둔다.
   */
  long spotPoi() {
    var ids = jdbc.queryForList("""
        SELECT poi_id FROM pois p WHERE theme IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM visitor_photos v WHERE v.poi_id = p.poi_id)
        ORDER BY poi_id LIMIT 1""", Long.class);
    if (ids.isEmpty()) {
      fail("사진이 없는 화면 스팟이 없다 — 로컬 DB 의 visitor_photos 가 스팟을 전부 채웠다. 단언을 풀지 말고 그 행을 확인할 것");
    }
    return ids.get(0);
  }

  long nonSpotPoi() {
    return jdbc.queryForObject(
        "SELECT poi_id FROM pois WHERE theme IS NULL AND poi_kind <> 'TERMINAL' ORDER BY poi_id LIMIT 1",
        Long.class);
  }

  long missingPoi() {
    return jdbc.queryForObject("SELECT COALESCE(max(poi_id), 0) + 1000 FROM pois", Long.class);
  }

  long missingPhoto() {
    return jdbc.queryForObject("SELECT COALESCE(max(photo_id), 0) + 1000 FROM visitor_photos",
        Long.class);
  }

  /** 이 테스트 계정들이 올린 사진 행 수(남 역할 제외). @BeforeEach 가 비우므로 거부 뒤에는 0 이어야 한다. */
  private int testPhotoRows() {
    return jdbc.queryForObject("""
        SELECT count(*) FROM visitor_photos v JOIN users u ON u.user_id = v.user_id
        WHERE u.provider = 'KAKAO' AND u.oauth_id LIKE ? AND u.oauth_id <> ?""",
        Integer.class, TEST_USERS + "%", BYSTANDER);
  }

  private int count(String table, long photoId) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE photo_id = ?",
        Integer.class, photoId);
  }
}
