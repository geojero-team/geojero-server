package com.example.geojeroserver.tour;

import java.util.List;

/**
 * 관광사진 API(PhotoGalleryService1) HTTP 계층 추상.
 *
 * KorService2와 **다른 서비스·다른 키**다. 이쪽 사진은 공공누리 제1유형이라 출처만
 * 밝히면 쓸 수 있다 — KorService2 사진이 장마다 Type3로 막히는 것과 다르다.
 * 실패는 예외로.
 */
public interface PhotoGalleryGateway {
  /** location은 촬영지(galPhotographyLocation) — 같은 이름이 다른 지역에도 있어서 필요하다. */
  record GalleryPhoto(String title, String location, String imageUrl) {}

  List<GalleryPhoto> search(String keyword) throws Exception;

  /** 키가 없으면 호출해도 실패하므로 카운터를 태우면 안 된다. */
  default boolean isConfigured() {
    return true;
  }
}
