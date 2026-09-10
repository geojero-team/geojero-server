package com.example.geojeroserver.tour;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 한도 방어 3층 단위 검증 — Spring·DB 무관. */
class TourApiClientTest {

  @Test void 실패시_단일폴백_이유와시각() {
    var client = new TourApiClient(
        (service, id) -> { throw new IllegalStateException("TourAPI HTTP 500"); },
        () -> true);
    var d = client.detail("129479", "ko", "자체 소개문");
    assertEquals("FALLBACK", d.get("source"));
    assertEquals("관광정보 확인 실패", d.get("reason"));
    assertEquals("자체 소개문", d.get("intro"));
    assertNotNull(d.get("checkedAt"));
  }

  @Test void 한도초과시_호출없이_폴백() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        (service, id) -> { calls.incrementAndGet();
          return new TourApiGateway.TourDetail("overview", null); },
        () -> false); // 카운터가 차단
    var d = client.detail("129479", "ko", null);
    assertEquals("FALLBACK", d.get("source"));
    assertEquals(0, calls.get()); // HTTP를 아예 안 탔다
  }

  @Test void 캐시히트_1회호출_영문이미지_보류() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        (service, id) -> { calls.incrementAndGet();
          return new TourApiGateway.TourDetail("영문 개요", "http://img"); },
        () -> true);
    var d1 = client.detail("1875200", "en", null);
    var d2 = client.detail("1875200", "en", null);
    assertEquals("TourAPI", d1.get("source"));
    assertNull(d1.get("imageUrl")); // 영문 이미지 저작권 [미확인] — 보류
    assertEquals(1, calls.get());   // 두 번째는 캐시
    assertSame(d1, d2);
  }

  @Test void 키가없으면_호출도_카운터도_소모없음() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        new TourApiGateway() {
          @Override public TourDetail fetch(String service, String id) {
            calls.incrementAndGet();
            return new TourDetail("overview", null);
          }
          @Override public boolean isConfigured() { return false; } // TOURAPI_KEY 미설정
        },
        () -> { throw new AssertionError("카운터를 건드리면 안 됨"); });
    var d = client.detail("129479", "ko", "자체 소개문");
    assertEquals("FALLBACK", d.get("source"));
    assertEquals("자체 소개문", d.get("intro"));
    assertEquals(0, calls.get());
  }

  @Test void contentId없으면_호출도_카운터도_소모없음() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        (service, id) -> { calls.incrementAndGet(); return null; },
        () -> { throw new AssertionError("카운터를 건드리면 안 됨"); });
    var d = client.detail(null, "ko", "폴백문");
    assertEquals("FALLBACK", d.get("source"));
    assertEquals(0, calls.get());
  }

  /** 사진 저작권은 POI가 아니라 장 단위다 — 도장포처럼 대표만 Type3인 곳을 살린다. */
  @Test void 사진목록은_Type3만_빼고_중복도_지운다() {
    var client = new TourApiClient(
        new TourApiGateway() {
          @Override public TourDetail fetch(String service, String id) {
            return new TourDetail("개요", "http://a.jpg", "Type1");
          }
          @Override public List<TourApiGateway.TourImage> images(String service, String id) {
            return List.of(
                new TourImage("http://a.jpg", "Type1"),   // 대표와 같은 장 → 하나로
                new TourImage("http://b.jpg", "Type3"),   // 제3자 저작권 → 보류
                new TourImage("http://c.jpg", "Type1"),
                new TourImage(null, "Type1"));            // 빈 URL → 버림
          }
        },
        () -> true);
    assertEquals(List.of("https://a.jpg", "https://c.jpg"), client.images("127182", "ko"));
  }

  @Test void 대표사진이_Type3면_소개는_남기고_사진만_뺀다() {
    var client = new TourApiClient(
        (service, id) -> new TourApiGateway.TourDetail("개요 원문", "http://x.jpg", "Type3"),
        () -> true);
    var d = client.detail("129508", "ko", null);
    assertEquals("TourAPI", d.get("source"));
    assertEquals("개요 원문", d.get("overview")); // 저작권은 사진 얘기지 글 얘기가 아니다
    assertNull(d.get("imageUrl"));
  }

  @Test void 사진조회가_실패해도_빈목록일뿐_예외가_새지않는다() {
    var client = new TourApiClient(
        new TourApiGateway() {
          @Override public TourDetail fetch(String service, String id) {
            return new TourDetail("개요", null);
          }
          @Override public List<TourApiGateway.TourImage> images(String service, String id) {
            throw new IllegalStateException("TourAPI HTTP 500");
          }
        },
        () -> true);
    assertEquals(List.of(), client.images("129479", "ko"));
  }

  @Test void 사진도_캐시된다_두번불러도_한번만_호출() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        new TourApiGateway() {
          @Override public TourDetail fetch(String service, String id) {
            return new TourDetail("개요", null);
          }
          @Override public List<TourApiGateway.TourImage> images(String service, String id) {
            calls.incrementAndGet();
            return List.of(new TourImage("https://a.jpg", "Type1"));
          }
        },
        () -> true);
    assertEquals(client.images("129479", "ko"), client.images("129479", "ko"));
    assertEquals(1, calls.get());
  }

  /**
   * 관광사진은 키워드 검색이라 남의 사진이 섞여 온다. 실제로 '도장포'로 찾으면 인근
   * 바람의언덕 사진이 딸려 오고, '신선대'로 찾으면 부산 신선대가 나온다(2026-09-10 실측).
   * 걸러내지 못하면 엉뚱한 곳 사진을 그 스팟이라고 내보이게 된다.
   */
  @Test void 관광사진은_제목과_촬영지_둘다_맞아야_쓴다() {
    var client = new TourApiClient(
        (service, id) -> new TourApiGateway.TourDetail("개요", null),
        keyword -> List.of(
            new PhotoGalleryGateway.GalleryPhoto(
                "도장포 유람선선착장", "경상남도 거제시 남부면", "http://a.jpg"),
            new PhotoGalleryGateway.GalleryPhoto(   // 제목에 키워드가 없다 — 인근의 남의 사진
                "거제 바람의 언덕", "경상남도 거제시 남부면", "http://b.jpg"),
            new PhotoGalleryGateway.GalleryPhoto(   // 거제가 아니다 — 같은 이름 다른 고장
                "도장포 선착장", "부산광역시 남구", "http://c.jpg"),
            new PhotoGalleryGateway.GalleryPhoto(
                "도장포 마을", "경상남도 거제시", "http://d.jpg")),
        () -> true);
    assertEquals(List.of("https://a.jpg", "https://d.jpg"), client.galleryPhotos("도장포"));
  }

  @Test void 관광사진_키가없으면_호출도_카운터도_소모없음() {
    var client = new TourApiClient(
        (service, id) -> new TourApiGateway.TourDetail("개요", null),
        new PhotoGalleryGateway() {
          @Override public List<GalleryPhoto> search(String keyword) {
            throw new AssertionError("키가 없으면 부르면 안 됨");
          }
          @Override public boolean isConfigured() { return false; }
        },
        () -> { throw new AssertionError("카운터를 건드리면 안 됨"); });
    assertEquals(List.of(), client.galleryPhotos("도장포"));
  }

  /** 0건도 캐시한다 — 없는 걸 확인하려고 매 요청 한도를 태우면 안 된다(명사해수욕장). */
  @Test void 관광사진_0건도_캐시되어_두번째는_호출없음() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(
        (service, id) -> new TourApiGateway.TourDetail("개요", null),
        keyword -> { calls.incrementAndGet(); return List.of(); },
        () -> true);
    assertEquals(List.of(), client.galleryPhotos("명사"));
    assertEquals(List.of(), client.galleryPhotos("명사"));
    assertEquals(1, calls.get());
  }

  @Test void 영문사진은_보류라_호출도_카운터도_소모없음() {
    var client = new TourApiClient(
        new TourApiGateway() {
          @Override public TourDetail fetch(String service, String id) {
            return new TourDetail("overview", null);
          }
          @Override public List<TourApiGateway.TourImage> images(String service, String id) {
            throw new AssertionError("영문은 부르면 안 됨");
          }
        },
        () -> { throw new AssertionError("카운터를 건드리면 안 됨"); });
    assertEquals(List.of(), client.images("1875200", "en"));
  }
}
