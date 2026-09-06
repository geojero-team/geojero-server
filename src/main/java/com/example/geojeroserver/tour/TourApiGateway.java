package com.example.geojeroserver.tour;

/** TourAPI HTTP 계층 추상 — 테스트에서 목킹하는 유일한 지점. 실패는 예외로. */
public interface TourApiGateway {
  record TourDetail(String overview, String imageUrl) {}

  TourDetail fetch(String service, String contentId) throws Exception;
}
