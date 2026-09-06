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
  private static final long TTL_SEC = 7 * 24 * 3600;

  private final byte[] key;

  public SessionCookies(@Value("${SESSION_SECRET:}") String secret) {
    if (secret == null || secret.isBlank()) {
      key = new byte[32];
      new SecureRandom().nextBytes(key);
      LoggerFactory.getLogger(SessionCookies.class)
          .warn("SESSION_SECRET 미설정 — 임시 키 사용 (재기동 시 전 세션 무효)");
    } else {
      key = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  public ResponseCookie issue(long userId) {
    long exp = Instant.now().getEpochSecond() + TTL_SEC;
    String payload = userId + "." + exp;
    return ResponseCookie.from(NAME, payload + "." + sign(payload))
        .httpOnly(true).sameSite("Lax").path("/")
        .maxAge(Duration.ofSeconds(TTL_SEC)).build();
  }

  public ResponseCookie clear() {
    return ResponseCookie.from(NAME, "")
        .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build();
  }

  /** 검증 실패는 null — 401 변환은 require()가 한다. */
  public Long verify(HttpServletRequest req) {
    if (req.getCookies() == null) return null;
    for (Cookie c : req.getCookies()) {
      if (!NAME.equals(c.getName())) continue;
      String[] p = c.getValue().split("\\.");
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
    return null;
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
