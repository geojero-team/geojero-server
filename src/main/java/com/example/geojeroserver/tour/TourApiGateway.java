package com.example.geojeroserver.tour;

import java.util.List;
import java.util.Map;

/** TourAPI HTTP 계층 추상 — 테스트에서 목킹하는 유일한 지점. 실패는 예외로. */
public interface TourApiGateway {
  /**
   * cpyrhtDivCd는 대표 사진(imageUrl)의 저작권 구분이다 — overview가 아니라.
   * addr1은 TourAPI 주소 원문(스팟 상세 주소 줄, Figma 02-2 `607:4`) — 우리 DB에 주소 컬럼이 없어 런타임 값만 쓴다.
   */
  record TourDetail(String overview, String imageUrl, String cpyrhtDivCd, String addr1) {
    /** 주소를 안 보는 호출부(옛 테스트)용. */
    public TourDetail(String overview, String imageUrl, String cpyrhtDivCd) {
      this(overview, imageUrl, cpyrhtDivCd, null);
    }

    /** 저작권을 따지지 않는 호출부(사진을 안 보는 테스트)용. */
    public TourDetail(String overview, String imageUrl) {
      this(overview, imageUrl, null, null);
    }
  }

  /**
   * 추가 사진 한 장. cpyrhtDivCd는 이미지 단위 저작권 구분(Type3 = 공공누리 제3유형 — 스팟은 쓰지 않고 맛집 · 숙소는 원본 그대로 쓴다).
   * serialnum 은 등록 번호(「4057087_1」 — 끝 번호가 등록 순서). 영문 사진을 등록 순으로 놓을 때 쓴다.
   */
  record TourImage(String url, String cpyrhtDivCd, String serialnum) {
    public TourImage(String url, String cpyrhtDivCd) {
      this(url, cpyrhtDivCd, null);
    }
  }

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

  /**
   * 음식점 메뉴 사진(detailImage2 imageYN=N). 추가 사진과 같은 오퍼레이션을 한 번 더 부른다.
   * default 빈 목록 — 함수형 인터페이스를 지킨다.
   */
  default List<TourImage> menuImages(String service, String contentId) throws Exception {
    return List.of();
  }

  /**
   * 소개 정보(detailIntro2) — 맛집(39) 영업시간 · 쉬는 날 · 대표 메뉴, 숙소(32) 체크인 · 체크아웃 · 부대시설 등.
   * 필드 이름 그대로의 원문 문자열 지도. 별개 오퍼레이션이라 호출이 한 건 더 든다. default 빈 지도 — 함수형 인터페이스를 지킨다.
   */
  default Map<String, String> intro(String service, String contentId, String contentTypeId) throws Exception {
    return Map.of();
  }
}
