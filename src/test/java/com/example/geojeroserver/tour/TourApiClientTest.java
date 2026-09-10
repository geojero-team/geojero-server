package com.example.geojeroserver.tour;

import static org.junit.jupiter.api.Assertions.*;

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
}
