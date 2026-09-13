package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.photos.TestImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 방문자 사진 올리기를 **실제 Tomcat 위에서** HTTP 로 부른다.
 *
 * MockMvc 의 multipart() 는 서블릿 컨테이너의 multipart 파싱을 거치지 않는다. 그래서
 * 업로드 한도(스프링 기본 max-file-size 1MB)와 파트 글자 인코딩은 MockMvc 로 확인할 수 없다.
 * 설정은 바꾸지 않는다 — 기본값이 실제로 어떻게 동작하는지를 지킨다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisitorPhotoHttpTest {
  static final String USER = "test-oauth-visitorphotos-http";
  private static final String BOUNDARY = "geojero-test-boundary-7f3a";

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionCookies sessions;

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

  @BeforeEach
  void clearPhotos() {
    jdbc.update("""
        DELETE FROM visitor_photos WHERE user_id IN
          (SELECT user_id FROM users WHERE provider = 'KAKAO' AND oauth_id = ?)""", USER);
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users WHERE provider = 'KAKAO' AND oauth_id = ?", USER);
  }

  /**
   * 1MB 를 넘으면 413. 컨트롤러에 닿기 전에 DispatcherServlet 이 multipart 를 풀다가 막는다.
   * 1.5MB 는 Tomcat 이 남은 본문을 삼키는 한도(max-swallow-size 2MB) 안이라 응답이 돌아온다.
   */
  @Test void 한도를_넘는_업로드는_413_행이_남지_않는다() throws Exception {
    byte[] big = new byte[1_500_000];
    big[0] = (byte) 0xFF;
    big[1] = (byte) 0xD8;
    big[2] = (byte) 0xFF;

    HttpResponse<String> res = upload(big, null);

    assertEquals(413, res.statusCode(), res.body());
    assertEquals(413, new ObjectMapper().readTree(res.body()).path("status").asInt());
    assertEquals(0, rows());
  }

  /**
   * 브라우저 FormData 는 캡션 파트에 charset 을 붙이지 않는다. 실제 Tomcat 이 그 파트를
   * UTF-8 로 읽어야 한글·이모지 캡션이 깨지지 않는다. MockMvc 의 param() 은 이 경로를 건너뛴다.
   */
  @Test void 실제_HTTP로_보낸_한글_이모지_캡션이_그대로_저장된다() throws Exception {
    String caption = "몽돌 소리가 좋았다 😀";

    HttpResponse<String> res = upload(TestImages.jpeg(40, 30), caption);

    assertEquals(201, res.statusCode(), res.body());
    long photoId = new ObjectMapper().readTree(res.body()).path("photoId").asLong();
    assertEquals(caption, new ObjectMapper().readTree(res.body()).path("caption").asText());
    assertEquals(caption, jdbc.queryForObject(
        "SELECT caption FROM visitor_photos WHERE photo_id = ?", String.class, photoId));
  }

  private HttpResponse<String> upload(byte[] file, String caption) throws Exception {
    long uid = jdbc.queryForObject("""
        INSERT INTO users (provider, oauth_id) VALUES ('KAKAO', ?)
        ON CONFLICT (provider, oauth_id) DO UPDATE SET updated_at = now()
        RETURNING user_id""", Long.class, USER);
    long poi = jdbc.queryForObject(
        "SELECT poi_id FROM pois WHERE theme IS NOT NULL ORDER BY poi_id LIMIT 1", Long.class);

    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(("--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
        + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    body.writeBytes(file);
    body.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
    if (caption != null) {
      body.writeBytes(("--" + BOUNDARY + "\r\n"
          + "Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
          .getBytes(StandardCharsets.US_ASCII));
      body.writeBytes(caption.getBytes(StandardCharsets.UTF_8));
      body.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
    body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));

    HttpRequest req = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/pois/" + poi + "/visitor-photos"))
        .timeout(Duration.ofSeconds(20))
        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
        .header("Authorization", "Bearer " + sessions.issueToken(uid))
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private int rows() {
    return jdbc.queryForObject("""
        SELECT count(*) FROM visitor_photos v JOIN users u ON u.user_id = v.user_id
        WHERE u.provider = 'KAKAO' AND u.oauth_id = ?""", Integer.class, USER);
  }
}
