package com.example.geojeroserver.tour;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

  private record ImageEntry(List<String> urls, long at) {}

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final Map<String, ImageEntry> imageCache = new ConcurrentHashMap<>();
  private final TourApiGateway gateway;
  private final CallCounter counter;

  public TourApiClient(TourApiGateway gateway, CallCounter counter) {
    this.gateway = gateway;
    this.counter = counter;
  }

  /** lang: "ko"|"en". contentId·키가 없으면 호출·카운터 소모 없이 폴백. */
  public Map<String, Object> detail(String contentId, String lang, String introFallback) {
    if (contentId == null || contentId.isBlank()) return fallback(introFallback);
    // 키가 없으면 호출은 어차피 실패한다. 여기서 막지 않으면 실패가 카운터를 태우고,
    // 실패는 캐시에도 안 남으므로 요청마다 반복된다 → 키를 나중에 넣어도 그날은 폴백만 나온다.
    if (!gateway.isConfigured()) return fallback(introFallback);
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
      // 영문 이미지는 [미확인] 보류. Type3(제3자 저작권)도 뺀다 — 판단은 POI가 아니라
      // 사진 한 장 단위다. http로 오는 URL이 섞여 있어 https로 올린다 — 화면이 https라
      // http 이미지는 브라우저가 혼합 콘텐츠로 막고 빈칸만 남는다.
      boolean usable = !"en".equals(lang) && !"Type3".equals(d.cpyrhtDivCd());
      detail.put("imageUrl", usable ? https(d.imageUrl()) : null);
      cache.put(key, new CacheEntry(detail, System.currentTimeMillis()));
      return detail;
    } catch (Exception e) {
      return fallback(introFallback);
    }
  }

  /**
   * 상세 화면용 추가 사진들. detail()과 **별개 오퍼레이션**이라 호출이 한 건 더 든다 —
   * 그래서 목록(/api/pois)에서는 부르지 않는다. POI 11곳 × 2건이 되면 개발계정 한도가
   * 하루치를 며칠 만에 태운다.
   *
   * 실패·한도·영문은 전부 빈 목록이다. 사진이 없다고 상세가 죽으면 안 된다(절대규칙 3).
   */
  public List<String> images(String contentId, String lang) {
    if (contentId == null || contentId.isBlank()) return List.of();
    if (!gateway.isConfigured()) return List.of();
    if ("en".equals(lang)) return List.of();   // 영문 이미지 저작권 [미확인] — 보류
    String key = "KorService2:" + contentId;
    var hit = imageCache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.urls();
    if (!counter.tryAcquire()) return List.of();
    try {
      List<String> urls = new ArrayList<>();
      for (var im : gateway.images("KorService2", contentId)) {
        if (im.url() == null || im.url().isBlank()) continue;
        // 저작권은 장마다 다르다. Type3(제3자)만 빼고 나머지는 출처표시로 쓴다 —
        // POI를 통째로 막으면 같은 장소의 쓸 수 있는 사진까지 함께 잃는다(기준문서 §9).
        if ("Type3".equals(im.cpyrhtDivCd())) continue;
        String u = https(im.url());
        if (!urls.contains(u)) urls.add(u);
      }
      var frozen = List.copyOf(urls);
      imageCache.put(key, new ImageEntry(frozen, System.currentTimeMillis()));
      return frozen;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static String https(String url) {
    return url != null && url.startsWith("http://") ? "https://" + url.substring(7) : url;
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
