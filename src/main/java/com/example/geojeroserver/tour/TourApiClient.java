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

  /** 촬영지에 이걸 포함하는 사진만 쓴다 — '신선대'는 부산에도 있다. */
  private static final String REGION = "거제";

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final Map<String, ImageEntry> imageCache = new ConcurrentHashMap<>();
  private final TourApiGateway gateway;
  private final PhotoGalleryGateway photoGateway;
  private final CallCounter counter;

  // 생성자가 둘이라 어느 쪽을 쓸지 Spring에 알려줘야 한다 — 없으면 기본 생성자를 찾다 죽는다.
  @org.springframework.beans.factory.annotation.Autowired
  public TourApiClient(TourApiGateway gateway, PhotoGalleryGateway photoGateway,
      CallCounter counter) {
    this.gateway = gateway;
    this.photoGateway = photoGateway;
    this.counter = counter;
  }

  /** 사진 갤러리를 안 보는 호출부(대부분의 테스트)용. */
  public TourApiClient(TourApiGateway gateway, CallCounter counter) {
    this(gateway, keyword -> java.util.List.of(), counter);
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
      // 대표 사진이 없으면 TourAPI 는 firstimage 를 **빈 문자열**로 준다(공곶이 2536196, 2026-09-15 운영에서 잡음).
      // 그대로 담으면 호출부가 "사진 있음"으로 보고 관광사진·추가 사진 폴백을 건너뛰어 목록 카드가 자리 그림으로 나가고,
      // 상세 사진 목록 첫 장이 빈 주소가 되어 깨진다. 없는 것은 null 이다.
      String first = d.imageUrl();
      detail.put("imageUrl", usable && first != null && !first.isBlank() ? https(first) : null);
      // 주소는 addr1 원문 그대로(스팟 상세 주소 줄, Figma 02-2 `607:4`). 빈 값은 null — 화면이 그 줄을 안 그린다.
      String addr = d.addr1();
      detail.put("address", addr == null || addr.isBlank() ? null : addr);
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
   * 예외 하나(2026-09-15): 대표 사진도 관광사진도 없는 **화면 스팟**의 목록 썸네일(PoiController.withImage).
   * 지금은 공곶이(V30) 한 곳이고, 상세와 같은 캐시 키라 상세를 한 번 연 뒤로는 호출이 없다.
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

  /**
   * 관광사진 API 폴백. KorService2 사진이 전 장 Type3라 하나도 못 쓰는 POI를 위한 것이다
   * (도장포유람선·신선대). 이쪽은 공공누리 제1유형이라 출처만 밝히면 쓸 수 있다.
   *
   * 키워드 검색이라 남의 사진이 섞인다 — '도장포'로 찾으면 인근 바람의언덕 사진이 7장
   * 딸려 오고, '신선대'로 찾으면 부산 신선대가 나온다. 그래서 **제목에 키워드가 들어가고
   * 촬영지가 거제인 것**만 남긴다. 둘 중 하나라도 없으면 엉뚱한 곳 사진을 그 스팟이라고
   * 내보이게 된다.
   */
  public List<String> galleryPhotos(String keyword) {
    if (keyword == null || keyword.isBlank()) return List.of();
    if (!photoGateway.isConfigured()) return List.of();
    String key = "PHOTO:" + keyword;
    var hit = imageCache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.urls();
    if (!counter.tryAcquire()) return List.of();
    try {
      List<String> urls = new ArrayList<>();
      for (var p : photoGateway.search(keyword)) {
        if (p.imageUrl() == null || p.imageUrl().isBlank()) continue;
        if (p.title() == null || !p.title().contains(keyword)) continue;
        if (p.location() == null || !p.location().contains(REGION)) continue;
        String u = https(p.imageUrl());
        if (!urls.contains(u)) urls.add(u);
      }
      var frozen = List.copyOf(urls);
      // 0건도 캐시한다 — 없는 걸 확인하려고 매 요청 카운터를 태우지 않는다.
      imageCache.put(key, new ImageEntry(frozen, System.currentTimeMillis()));
      return frozen;
    } catch (Exception e) {
      return List.of();
    }
  }

  // ── 맛집 · 숙소 (2026-09-19 사용자 결정 — 기준문서 §6 「맛집 · 숙소」) ─────────────────────────────

  /**
   * 맛집 · 숙소 한 곳의 TourAPI 정보. 글자는 {@link #clean} 을 거친다(줄바꿈 태그 → 줄바꿈, 엔티티 풀기 — 글 자체는 그대로).
   * intro 는 detailIntro2 필드 이름 그대로(opentimefood · restdatefood · firstmenu · checkintime · checkouttime · subfacility …),
   * 빈 값은 담지 않는다. firstImage 는 대표 사진(Type3 도 쓴다), 없으면 null.
   */
  public record PlaceInfo(String overview, String address, String firstImage, Map<String, String> intro) {}

  private record PlaceEntry(PlaceInfo info, long at) {}

  private final Map<String, PlaceEntry> placeCache = new ConcurrentHashMap<>();

  /**
   * detailCommon2 + detailIntro2 — 호출 두 건, 24h 캐시. 실패 · 한도 · 키 없음은 null 이다(호출부가 FALLBACK 을 그린다).
   * 스팟의 detail() 과 다르게 **Type3 대표 사진도 쓴다** — 공공누리 제3유형(출처표시 + 변경금지)은 원본 그대로면 쓸 수 있고
   * 맛집 · 숙소 화면은 사진을 자르지 않는다(기준문서 §7, 2026-09-19). 소개 정보를 못 받아도 공통 정보가 있으면 그것만으로 준다.
   */
  public PlaceInfo placeInfo(String contentId, String contentTypeId) {
    if (contentId == null || contentId.isBlank() || !gateway.isConfigured()) return null;
    String key = "PLACE:" + contentId;
    var hit = placeCache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.info();
    if (!counter.tryAcquire()) return null;
    try {
      var d = gateway.fetch("KorService2", contentId);
      if (d == null) return null;
      Map<String, String> intro = new LinkedHashMap<>();
      if (counter.tryAcquire()) {
        try {
          gateway.intro("KorService2", contentId, contentTypeId).forEach((k, v) -> {
            String c = clean(v);
            if (c != null) intro.put(k, c);
          });
        } catch (Exception ignored) {
          // 소개 정보만 못 받았다 — 공통 정보(주소 · 사진 · 소개문)는 그대로 낸다
        }
      }
      String first = d.imageUrl();
      var info = new PlaceInfo(clean(d.overview()), clean(d.addr1()),
          first == null || first.isBlank() ? null : https(first), Map.copyOf(intro));
      placeCache.put(key, new PlaceEntry(info, System.currentTimeMillis()));
      return info;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 맛집 · 숙소의 추가 사진(detailImage2) — Type3 도 쓴다(위와 같은 이유). 국문이 0장이고 영문 contentId 가 있으면
   * 영문 사진을 **등록 순(serialnum 끝 번호)** 으로 쓴다 — 한화리조트 거제 벨버디어(3445089): 영문 API 는 「로얄」을 먼저 주지만
   * 사용자가 대표로 고른 「스위트 오션뷰」가 _1 이다. 실패 · 한도는 빈 목록 — 사진이 없다고 상세가 죽지 않는다.
   */
  public List<String> placeImages(String contentId, String engContentId) {
    if (contentId == null || contentId.isBlank() || !gateway.isConfigured()) return List.of();
    String key = "PLACEIMG:" + contentId;
    var hit = imageCache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.urls();
    if (!counter.tryAcquire()) return List.of();
    try {
      List<String> urls = new ArrayList<>();
      for (var im : gateway.images("KorService2", contentId)) addImage(urls, im);
      if (urls.isEmpty() && engContentId != null && !engContentId.isBlank() && counter.tryAcquire()) {
        var eng = new ArrayList<>(gateway.images("EngService2", engContentId));
        eng.sort(java.util.Comparator.comparingInt(TourApiClient::serialOrder));
        for (var im : eng) addImage(urls, im);
      }
      var frozen = List.copyOf(urls);
      imageCache.put(key, new ImageEntry(frozen, System.currentTimeMillis()));
      return frozen;
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * 음식점 메뉴 사진(detailImage2 imageYN=N) — **등록 순**(serialnum 끝 번호), Type3 도 쓴다(맛집 · 숙소 사진과 같은 이유).
   * 백만석은 음식 사진이 이 칸에만 있다(2026-09-19 확인 — 12곳 중 이 칸이 있는 곳은 백만석 하나). 실패 · 한도는 빈 목록.
   */
  public List<String> placeMenuImages(String contentId) {
    if (contentId == null || contentId.isBlank() || !gateway.isConfigured()) return List.of();
    String key = "PLACEMENU:" + contentId;
    var hit = imageCache.get(key);
    if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.urls();
    if (!counter.tryAcquire()) return List.of();
    try {
      var menu = new ArrayList<>(gateway.menuImages("KorService2", contentId));
      menu.sort(java.util.Comparator.comparingInt(TourApiClient::serialOrder));
      List<String> urls = new ArrayList<>();
      for (var im : menu) addImage(urls, im);
      var frozen = List.copyOf(urls);
      imageCache.put(key, new ImageEntry(frozen, System.currentTimeMillis()));
      return frozen;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static void addImage(List<String> urls, TourApiGateway.TourImage im) {
    if (im.url() == null || im.url().isBlank()) return;
    String u = https(im.url());
    if (!urls.contains(u)) urls.add(u);
  }

  /** 「4057087_1」 → 1. 못 읽으면 맨 뒤. */
  private static int serialOrder(TourApiGateway.TourImage im) {
    String s = im.serialnum();
    if (s == null) return Integer.MAX_VALUE;
    int at = s.lastIndexOf('_');
    try {
      return Integer.parseInt(s.substring(at + 1));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  /**
   * TourAPI 글자 정리 — 줄바꿈 태그(br)를 줄바꿈으로, 나머지 태그는 빼고, HTML 엔티티(&amp;amp; 등)를 풀고, 줄마다 앞뒤 공백을 걷는다.
   * 글 자체(낱말 · 순서)는 바꾸지 않는다(원문 무수정). 비면 null.
   */
  public static String clean(String raw) {
    if (raw == null) return null;
    String s = raw.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
    s = org.springframework.web.util.HtmlUtils.htmlUnescape(s);
    String joined = s.lines().map(String::strip).filter(line -> !line.isEmpty())
        .collect(java.util.stream.Collectors.joining("\n"));
    return joined.isEmpty() ? null : joined;
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
