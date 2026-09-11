package com.example.geojeroserver.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 세션 서명키 주입 여부와, 그 사실을 밖에 알리는 방식.
 *
 * 이 클래스가 존재하는 이유: `SESSION_SECRET`이 설정됐는지를 EC2 밖에서 확인할 방법이 없어
 * 한 번 잘못 단정했다(2026-09-11). `application-prod.yml`에 줄이 없는 것은 미설정의 증거가
 * 아니다 — `@Value("${SESSION_SECRET:}")`는 OS 환경변수를 직접 읽는다.
 * 그래서 `/api/meta`가 **설정 여부만** 답한다. 값은 절대 나가지 않는다.
 */
class SessionCookiesTest {
  private static final String SECRET = "테스트-전용-가짜-값-아무-의미-없음";

  @Test void 시크릿이_있으면_configured() {
    assertTrue(new SessionCookies(SECRET).isSecretConfigured());
  }

  @Test void 빈값과_null은_임시키다() {
    // 공백만 있는 값은 '설정했다'로 쳐주지 않는다 — 서명키로 쓰면 사실상 무방비다.
    assertFalse(new SessionCookies(null).isSecretConfigured());
    assertFalse(new SessionCookies("").isSecretConfigured());
    assertFalse(new SessionCookies("   ").isSecretConfigured());
  }

  @Test void 시크릿이_고정이면_같은_토큰이_나온다() {
    // 이게 '재배포해도 로그인이 유지된다'의 실질이다. 키가 임시면 기동마다 달라진다.
    var a = new SessionCookies(SECRET).issueToken(7L);
    var b = new SessionCookies(SECRET).issueToken(7L);
    assertEquals(a, b);
  }

  @Test void 임시키는_기동마다_달라진다() {
    assertNotEquals(new SessionCookies("").issueToken(7L), new SessionCookies("").issueToken(7L));
  }

  @Test void 토큰에_시크릿이_들어가지_않는다() {
    // 서명은 HMAC 이라 원문이 복원되지 않지만, 실수로 payload 에 실리는 일만은 막는다.
    assertFalse(new SessionCookies(SECRET).issueToken(7L).contains(SECRET));
  }
}
