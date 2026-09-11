package com.example.geojeroserver.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * httpOnly 세션 쿠키 — 값은 "userId.만료초.HMAC-SHA256" (서버 저장 없음).
 * 시크릿은 SESSION_SECRET 환경변수. 미설정이면 기동마다 임시 키(재기동 시 전 세션 무효).
 */
@Component
public class SessionCookies {
  static final String NAME = "gj_session";
  private static final String BEARER = "Bearer ";
  private static final long TTL_SEC = 7 * 24 * 3600;

  private final byte[] key;
  private final boolean configured;

  public SessionCookies(@Value("${SESSION_SECRET:}") String secret) {
    configured = secret != null && !secret.isBlank();
    if (!configured) {
      key = new byte[32];
      new SecureRandom().nextBytes(key);
      LoggerFactory.getLogger(SessionCookies.class)
          .warn("SESSION_SECRET 미설정 — 임시 키 사용 (재기동 시 전 세션 무효)");
    } else {
      key = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  /**
   * 시크릿이 주입됐는가. **값도, 값에서 유도한 것도 절대 내보내지 않는다** — 예/아니오뿐이다.
   *
   * 왜 필요한가: 이게 없으면 "설정됐는지"를 밖에서 확인할 방법이 없다. EC2 로그나 `.env`를
   * 볼 수 있는 사람만 답할 수 있었고, 실제로 한 번 잘못 단정했다(2026-09-11). 설정 파일에
   * 줄이 없는 것은 미설정의 증거가 아니다 — `@Value`는 OS 환경변수를 직접 읽는다.
   * 헷갈리는 지점은 `.env`에 넣은 것과 컨테이너 `environment:`에 올라간 것이 다르다는 것이다.
   */
  public boolean isSecretConfigured() {
    return configured;
  }

  /**
   * 쿠키에 담는 값과 같은 토큰.
   *
   * 프론트(vercel.app)와 API(api.geojero.com)가 다른 사이트인 동안은 SameSite=Lax 쿠키가
   * 저장도 전송도 되지 않는다. SameSite=None 으로 열면 사파리가 서드파티 쿠키를 막는다.
   * 그래서 같은 토큰을 응답 본문으로도 내보내고 Authorization: Bearer 로 받는다.
   * 같은 사이트가 되면 쿠키가 먼저 잡히므로(verify 순서) 코드를 고칠 필요가 없다.
   */
  public String issueToken(long userId) {
    long exp = Instant.now().getEpochSecond() + TTL_SEC;
    String payload = userId + "." + exp;
    return payload + "." + sign(payload);
  }

  public ResponseCookie issue(long userId) {
    return ResponseCookie.from(NAME, issueToken(userId))
        .httpOnly(true).sameSite("Lax").path("/")
        .maxAge(Duration.ofSeconds(TTL_SEC)).build();
  }

  public ResponseCookie clear() {
    return ResponseCookie.from(NAME, "")
        .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build();
  }

  /**
   * 검증 실패는 null — 401 변환은 require()가 한다.
   * 쿠키를 먼저 보고, 없으면 Authorization: Bearer 를 본다. 같은 사이트가 되면 쿠키가 이긴다.
   */
  public Long verify(HttpServletRequest req) {
    if (req.getCookies() != null) {
      for (Cookie c : req.getCookies()) {
        if (NAME.equals(c.getName())) return validate(c.getValue());
      }
    }
    String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith(BEARER)) {
      return validate(auth.substring(BEARER.length()).trim());
    }
    return null;
  }

  private Long validate(String token) {
    if (token == null) return null;
    String[] p = token.split("\\.");
    if (p.length != 3) return null;
    try {
      long userId = Long.parseLong(p[0]);
      if (Long.parseLong(p[1]) < Instant.now().getEpochSecond()) return null;
      byte[] expect = signBytes(p[0] + "." + p[1]);
      byte[] got = HexFormat.of().parseHex(p[2]);
      return MessageDigest.isEqual(expect, got) ? userId : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  public long require(HttpServletRequest req) {
    Long uid = verify(req);
    if (uid == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }
    return uid;
  }

  private String sign(String payload) {
    return HexFormat.of().formatHex(signBytes(payload));
  }

  private byte[] signBytes(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
