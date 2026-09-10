package com.example.geojeroserver.tour;

/** TourAPI HTTP 계층 추상 — 테스트에서 목킹하는 유일한 지점. 실패는 예외로. */
public interface TourApiGateway {
  record TourDetail(String overview, String imageUrl) {}

  TourDetail fetch(String service, String contentId) throws Exception;

  /**
   * 키가 설정돼 있는가. 없으면 호출해도 실패하므로 일일 카운터를 태우면 안 된다.
   * default true — 테스트가 이 인터페이스를 람다로 스텁하므로 함수형 인터페이스를 유지한다.
   */
  default boolean isConfigured() {
    return true;
  }
}
