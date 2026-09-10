package com.example.geojeroserver.tour;

import java.util.List;

/** TourAPI HTTP 계층 추상 — 테스트에서 목킹하는 유일한 지점. 실패는 예외로. */
public interface TourApiGateway {
  /** cpyrhtDivCd는 대표 사진(imageUrl)의 저작권 구분이다 — overview가 아니라. */
  record TourDetail(String overview, String imageUrl, String cpyrhtDivCd) {
    /** 저작권을 따지지 않는 호출부(사진을 안 보는 테스트)용. */
    public TourDetail(String overview, String imageUrl) {
      this(overview, imageUrl, null);
    }
  }

  /** 추가 사진 한 장. cpyrhtDivCd는 이미지 단위 저작권 구분(Type3 = 제3자, 사용 보류). */
  record TourImage(String url, String cpyrhtDivCd) {}

  TourDetail fetch(String service, String contentId) throws Exception;

  /**
   * 키가 설정돼 있는가. 없으면 호출해도 실패하므로 일일 카운터를 태우면 안 된다.
   * default true — 테스트가 이 인터페이스를 람다로 스텁하므로 함수형 인터페이스를 유지한다.
   */
  default boolean isConfigured() {
    return true;
  }

  /**
   * 콘텐츠의 추가 사진들(detailImage2). fetch와 별개 오퍼레이션이라 호출이 한 건 더 든다.
   * default 빈 목록 — isConfigured와 같은 이유로 함수형 인터페이스를 지킨다.
   */
  default List<TourImage> images(String service, String contentId) throws Exception {
    return List.of();
  }
}
