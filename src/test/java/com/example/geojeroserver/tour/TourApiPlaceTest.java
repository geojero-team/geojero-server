package com.example.geojeroserver.tour;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 맛집 · 숙소(2026-09-19) 의 TourAPI 층 — Spring · DB 무관.
 * 스팟과 다른 점: Type3 사진도 쓴다(공공누리 제3유형 = 원본 그대로면 쓸 수 있다 — 화면이 자르지 않는다, 기준문서 §7),
 * detailIntro2(영업시간 · 체크인 등)를 같이 부른다, 국문 사진이 0장이면 영문 사진을 쓴다(한화 — 사용자 결정).
 */
class TourApiPlaceTest {

  @Test void 정보는_소개_주소_대표사진_소개정보를_주고_글의_br은_줄바꿈이다() {
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) {
        return new TourDetail("멸치<br>쌈밥 &amp; 회", "http://tong.visitkorea.or.kr/a.jpg", "Type3", "경상남도 거제시 일운면 지세포해안로 12");
      }
      @Override public Map<String, String> intro(String service, String id, String typeId) {
        assertEquals("39", typeId);
        return Map.of("opentimefood", "- 10:30~20:30<br />- 준비시간 15:00~17:00", "restdatefood", "매월 두번째·네번째 수요일", "packing", "");
      }
    }, () -> true);

    var info = client.placeInfo("2858010", "39");
    assertEquals("멸치\n쌈밥 & 회", info.overview());
    assertEquals("경상남도 거제시 일운면 지세포해안로 12", info.address());
    // Type3 여도 쓴다 — 맛집 · 숙소 화면은 사진을 자르지 않는다. http 는 https 로(혼합 콘텐츠 차단)
    assertEquals("https://tong.visitkorea.or.kr/a.jpg", info.firstImage());
    assertEquals("- 10:30~20:30\n- 준비시간 15:00~17:00", info.intro().get("opentimefood"));
    assertEquals("매월 두번째·네번째 수요일", info.intro().get("restdatefood"));
    assertFalse(info.intro().containsKey("packing"), "빈 값은 없는 것이다");
  }

  @Test void 정보는_24시간_캐시한다() {
    var calls = new AtomicInteger();
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) {
        calls.incrementAndGet();
        return new TourDetail("개요", null);
      }
      @Override public Map<String, String> intro(String service, String id, String typeId) {
        calls.incrementAndGet();
        return Map.of();
      }
    }, () -> true);
    assertSame(client.placeInfo("976736", "32"), client.placeInfo("976736", "32"));
    assertEquals(2, calls.get());
  }

  @Test void 정보를_못받으면_null_이고_키가_없으면_부르지도_않는다() {
    var failing = new TourApiClient((service, id) -> { throw new IllegalStateException("TourAPI HTTP 500"); }, () -> true);
    assertNull(failing.placeInfo("2858010", "39"));

    var noKey = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { throw new AssertionError("부르면 안 됨"); }
      @Override public boolean isConfigured() { return false; }
    }, () -> { throw new AssertionError("카운터를 건드리면 안 됨"); });
    assertNull(noKey.placeInfo("2858010", "39"));
  }

  @Test void 대표사진이_빈문자열이면_없는것이다() {
    var client = new TourApiClient((service, id) -> new TourApiGateway.TourDetail("개요", ""), () -> true);
    assertNull(client.placeInfo("2660777", "32").firstImage());
  }

  @Test void 사진은_Type3도_쓴다() {
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> images(String service, String id) {
        return List.of(new TourImage("http://tong.visitkorea.or.kr/1.jpg", "Type3"),
            new TourImage("https://tong.visitkorea.or.kr/2.jpg", "Type1"),
            new TourImage("https://tong.visitkorea.or.kr/2.jpg", "Type1"));
      }
    }, () -> true);
    assertEquals(List.of("https://tong.visitkorea.or.kr/1.jpg", "https://tong.visitkorea.or.kr/2.jpg"),
        client.placeImages("2858010", null));
  }

  /**
   * 한화리조트 거제 벨버디어는 국문 사진이 0장이라 영문(3445089) 객실 사진을 쓴다. 영문 API 는 「로얄」을 먼저 주지만
   * 사용자가 대표로 고른 「스위트 오션뷰」가 등록 번호 _1 이다 — 영문 사진은 등록 순(serialnum 끝 번호)으로 놓는다.
   */
  @Test void 국문사진이_0장이면_영문사진을_등록순으로_쓴다() {
    List<String> asked = new ArrayList<>();
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> images(String service, String id) {
        asked.add(service + ":" + id);
        if ("KorService2".equals(service)) return List.of();
        return List.of(new TourImage("https://tong.visitkorea.or.kr/cms/resource/76/4057076_image2_1.jpg", "Type3", "4057076_2"),
            new TourImage("https://tong.visitkorea.or.kr/cms/resource/87/4057087_image2_1.jpg", "Type3", "4057087_1"));
      }
    }, () -> true);
    assertEquals(List.of("https://tong.visitkorea.or.kr/cms/resource/87/4057087_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource/76/4057076_image2_1.jpg"),
        client.placeImages("2660777", "3445089"));
    assertEquals(List.of("KorService2:2660777", "EngService2:3445089"), asked);
  }

  @Test void 국문사진이_있으면_영문은_부르지_않는다() {
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> images(String service, String id) {
        if (!"KorService2".equals(service)) throw new AssertionError("영문은 부르면 안 됨");
        return List.of(new TourImage("https://tong.visitkorea.or.kr/1.jpg", "Type1"));
      }
    }, () -> true);
    assertEquals(List.of("https://tong.visitkorea.or.kr/1.jpg"), client.placeImages("2578495", "999"));
  }

  /** 음식점 메뉴 사진(detailImage2 imageYN=N) — 등록 순(serialnum 끝 번호)으로, Type3 도 쓴다. 백만석은 음식 사진이 이 칸에만 있다. */
  @Test void 메뉴_사진은_등록순으로_받는다() {
    List<String> asked = new ArrayList<>();
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> menuImages(String service, String id) {
        asked.add(service + ":" + id);
        return List.of(new TourImage("http://tong.visitkorea.or.kr/m3.JPG", "Type3", "3043327_3"),
            new TourImage("http://tong.visitkorea.or.kr/m1.JPG", "Type3", "3043329_1"),
            new TourImage("http://tong.visitkorea.or.kr/m2.JPG", "Type3", "3043328_2"));
      }
    }, () -> true);
    assertEquals(List.of("https://tong.visitkorea.or.kr/m1.JPG", "https://tong.visitkorea.or.kr/m2.JPG", "https://tong.visitkorea.or.kr/m3.JPG"),
        client.placeMenuImages("578976"));
    assertEquals(List.of("KorService2:578976"), asked);
  }

  @Test void 메뉴_사진_실패는_빈목록() {
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> menuImages(String service, String id) { throw new IllegalStateException("HTTP 500"); }
    }, () -> true);
    assertEquals(List.of(), client.placeMenuImages("578976"));
  }

  @Test void 사진_실패는_빈목록() {
    var client = new TourApiClient(new TourApiGateway() {
      @Override public TourDetail fetch(String service, String id) { return new TourDetail("개요", null); }
      @Override public List<TourImage> images(String service, String id) { throw new IllegalStateException("HTTP 500"); }
    }, () -> true);
    assertEquals(List.of(), client.placeImages("2858010", null));
  }
}
