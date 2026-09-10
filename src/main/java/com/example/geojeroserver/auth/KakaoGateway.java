package com.example.geojeroserver.auth;

/**
 * 카카오 인증 HTTP 경계 — 테스트는 이 인터페이스를 목으로 대체한다.
 * 이메일은 요청 자체를 하지 않는다(무수집 결정 — 반환 타입에 아예 없음).
 */
public interface KakaoGateway {
  record KakaoUser(String oauthId, String nickname) {}

  KakaoUser exchange(String code, String redirectUri) throws Exception;

  /**
   * 카카오 인증 화면 주소. REST 키가 없으면 null — 로그인만 못 하고 나머지는 그대로 돈다.
   * 키를 클라이언트에 내보내지 않으려고 서버가 만든다.
   */
  String authorizeUrl(String redirectUri);
}
