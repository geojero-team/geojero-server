package com.example.geojeroserver.tour;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 한도 방어 3층 (설계문서 §10):
 *  1층 인메모리 캐시 24h — POI 상세는 일 단위 변동. 재시작 리셋은 2·3층이 받친다
 *  2층 일일 카운터 soft 800 — 초과 시 당일 호출 중단
 *  3층 전 실패 유형 단일 폴백 — 한도·타임아웃·5xx·overview 부재 전부 같은 경로.
 *     폴백은 자체 데이터만으로 구성 → TourAPI 0건 상태에서도 화면 성립 ("이유 없는 빈칸" 금지)
 */
@Service
public class TourApiClient {
  static final long TTL_MS = 24 * 3600 * 1000L;

  private record CacheEntry(Map<String, Object> detail, long at) {}

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final TourApiGateway gateway;
  private final CallCounter counter;

  public TourApiClient(TourApiGateway gateway, CallCounter counter) {
    this.gateway = gateway;
    this.counter = counter;
  }

  /** lang: "ko"|"en". contentId 없으면 호출·카운터 소모 없이 폴백. */
  public Map<String, Object> detail(String contentId, String lang, String introFallback) {
    if (contentId == null || contentId.isBlank()) return fallback(introFallback);
    String service = "en".equals(lang) ? "EngService2" : "KorService2";
    String key = service + ":" + contentId;
    var hit = cache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.detail();
    if (!counter.tryAcquire()) return fallback(introFallback);
    try {
      var d = gateway.fetch(service, contentId);
      if (d == null || d.overview() == null || d.overview().isBlank()) {
        return fallback(introFallback);
      }
      Map<String, Object> detail = new LinkedHashMap<>();
      detail.put("source", "TourAPI");
      detail.put("overview", d.overview());                            // 원문 무수정
      detail.put("imageUrl", "en".equals(lang) ? null : d.imageUrl()); // 영문 이미지 [미확인] 보류
      cache.put(key, new CacheEntry(detail, System.currentTimeMillis()));
      return detail;
    } catch (Exception e) {
      return fallback(introFallback);
    }
  }

  private static Map<String, Object> fallback(String intro) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("source", "FALLBACK");
    m.put("intro", intro);
    m.put("reason", "관광정보 확인 실패");
    m.put("checkedAt", Instant.now().toString());
    return m;
  }
}
