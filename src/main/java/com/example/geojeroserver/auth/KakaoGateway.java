package com.example.geojeroserver.auth;

/**
 * 카카오 인증 HTTP 경계 — 테스트는 이 인터페이스를 목으로 대체한다.
 * 이메일은 요청 자체를 하지 않는다(무수집 결정 — 반환 타입에 아예 없음).
 */
public interface KakaoGateway {
  record KakaoUser(String oauthId, String nickname) {}

  KakaoUser exchange(String code, String redirectUri) throws Exception;
}
