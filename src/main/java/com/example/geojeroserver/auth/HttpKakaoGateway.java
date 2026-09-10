package com.example.geojeroserver.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 카카오 토큰 교환 + 사용자 조회 실호출. 키는 KAKAO_REST_KEY 환경변수(.env)로만. */
@Component
public class HttpKakaoGateway implements KakaoGateway {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${KAKAO_CLIENT_ID:}")
  String restKey;

  @Value("${KAKAO_CLIENT_SECRET:}")
  String clientSecret;

  @Override
  public String authorizeUrl(String redirectUri) {
    if (restKey == null || restKey.isBlank()) return null;
    return "https://kauth.kakao.com/oauth/authorize?response_type=code"
        + "&client_id=" + enc(restKey) + "&redirect_uri=" + enc(redirectUri);
  }

  @Override
  public KakaoUser exchange(String code, String redirectUri) throws Exception {
    String form = "grant_type=authorization_code"
        + "&client_id=" + enc(restKey) + "&redirect_uri=" + enc(redirectUri)
        + "&code=" + enc(code)
        + (clientSecret.isBlank() ? "" : "&client_secret=" + enc(clientSecret));
    HttpResponse<String> tokenRes = http.send(
        HttpRequest.newBuilder(URI.create("https://kauth.kakao.com/oauth/token"))
            .timeout(Duration.ofSeconds(4))
            .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
        HttpResponse.BodyHandlers.ofString());
    if (tokenRes.statusCode() != 200) {
      throw new IllegalStateException("kauth HTTP " + tokenRes.statusCode());
    }
    String accessToken = om.readTree(tokenRes.body()).path("access_token").asText(null);
    if (accessToken == null) {
      throw new IllegalStateException("kauth 응답에 access_token 없음");
    }

    // property_keys 미전달 = 기본 프로필만. kakao_account.email은 요청하지 않는다
    HttpResponse<String> meRes = http.send(
        HttpRequest.newBuilder(URI.create("https://kapi.kakao.com/v2/user/me"))
            .timeout(Duration.ofSeconds(4))
            .header("Authorization", "Bearer " + accessToken).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (meRes.statusCode() != 200) {
      throw new IllegalStateException("kapi HTTP " + meRes.statusCode());
    }
    var me = om.readTree(meRes.body());
    long id = me.path("id").asLong(0);
    if (id == 0) {
      throw new IllegalStateException("kapi 응답에 id 없음");
    }
    return new KakaoUser(Long.toString(id), me.path("properties").path("nickname").asText(null));
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
