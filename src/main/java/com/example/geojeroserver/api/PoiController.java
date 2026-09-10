package com.example.geojeroserver.api;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import com.example.geojeroserver.tour.TourApiClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoiController {
  // lat·lng는 좌표 미확보 POI가 있어 nullable(Double). 0.0으로 떨어지면 지도에 유령 핀이 찍힌다.
  // theme·region·category·shortName은 Figma가 분류한 POI만 값이 있고 나머지는 null이다.
  // imageUrl은 withImages=true일 때만 채운다 — TourAPI 실호출이라 기본 경로를 느리게 하지 않는다.
  public record PoiListItem(long poiId, String name, String shortName, String kind,
                            String theme, String region, String category, String tier,
                            boolean hasEnglish, Double lat, Double lng, String imageUrl) {}

  /** 이미지를 채우는 데만 쓰는 원본 값. 응답에는 나가지 않는다. */
  private record Row(PoiListItem item, String contentId, boolean imageUseOk, String intro) {}
  public record PoisRes(List<PoiListItem> pois) {}
  public record PoiDetailRes(long poiId, String name, String kind, String tier,
                             String lang, boolean langFallback, Map<String, Object> detail,
                             String checkUrl, String lastDeparture) {}

  /** numeric → Double. PgJDBC는 getObject(n, Double.class)를 numeric에 대해 지원하지 않는다. */
  private static Double toDouble(java.math.BigDecimal v) {
    return v == null ? null : v.doubleValue();
  }

  private final JdbcTemplate jdbc;
  private final TourApiClient tourApi;

  public PoiController(JdbcTemplate jdbc, TourApiClient tourApi) {
    this.jdbc = jdbc;
    this.tourApi = tourApi;
  }

  @GetMapping("/api/pois")
  public PoisRes list(@RequestParam(defaultValue = "false") boolean withImages) {
    var rows = jdbc.query("""
        SELECT p.poi_id, p.poi_name, p.short_name, p.poi_kind, p.theme, p.region,
               p.category, p.tier,
               EXISTS(SELECT 1 FROM poi_i18n i
                      WHERE i.poi_id = p.poi_id AND i.lang = 'EN'
                        AND i.matched_by = 'HUMAN') AS has_en,
               p.lat, p.lng, p.tour_content_id, p.image_use_ok, p.intro_text
        FROM pois p ORDER BY p.poi_id""",
        (rs, i) -> new Row(
            new PoiListItem(rs.getLong("poi_id"), rs.getString("poi_name"),
                rs.getString("short_name"), rs.getString("poi_kind"), rs.getString("theme"),
                rs.getString("region"), rs.getString("category"), rs.getString("tier"),
                rs.getBoolean("has_en"), toDouble(rs.getBigDecimal("lat")),
                toDouble(rs.getBigDecimal("lng")), null),
            rs.getString("tour_content_id"), rs.getBoolean("image_use_ok"),
            rs.getString("intro_text")));

    if (!withImages) return new PoisRes(rows.stream().map(Row::item).toList());

    // TourAPI는 POI마다 한 번씩 부른다. 순차로 돌면 첫 요청이 수십 초가 되므로 동시에 던진다.
    // 캐시(24h)가 채워진 뒤에는 호출이 없다. 개별 실패는 이미지 없이 내보낸다 — 목록이 죽지 않는다.
    List<PoiListItem> out = new ArrayList<>(rows.size());
    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = rows.stream().map(r -> exec.submit(() -> withImage(r))).toList();
      for (int i = 0; i < rows.size(); i++) {
        try {
          out.add(tasks.get(i).get());
        } catch (Exception e) {
          out.add(rows.get(i).item());
        }
      }
    }
    return new PoisRes(out);
  }

  /** image_use_ok=false는 부르지도 않는다 — cpyrhtDivCd Type3 보류(기준문서 §9). */
  private PoiListItem withImage(Row r) {
    if (!r.imageUseOk() || r.contentId() == null || r.contentId().isBlank()) return r.item();
    Object url = tourApi.detail(r.contentId(), "ko", r.intro()).get("imageUrl");
    if (url == null) return r.item();
    var it = r.item();
    return new PoiListItem(it.poiId(), it.name(), it.shortName(), it.kind(), it.theme(),
        it.region(), it.category(), it.tier(), it.hasEnglish(), it.lat(), it.lng(),
        url.toString());
  }

  @GetMapping("/api/pois/{poiId}")
  public PoiDetailRes detail(@PathVariable long poiId,
      @RequestParam(defaultValue = "ko") String lang) {
    return jdbc.queryForObject("""
        SELECT p.poi_id, p.poi_name, p.poi_kind, p.tier, p.intro_text, p.check_url,
               p.last_departure_time, p.tour_content_id, p.image_use_ok,
               (SELECT i.tour_content_id FROM poi_i18n i
                 WHERE i.poi_id = p.poi_id AND i.lang = 'EN'
                   AND i.matched_by = 'HUMAN') AS en_content_id
        FROM pois p WHERE p.poi_id = ?""",
        (rs, i) -> {
          String enContentId = rs.getString("en_content_id");
          boolean wantEn = "en".equalsIgnoreCase(lang);
          boolean fallbackLang = wantEn && enContentId == null; // 영문 미보유 → 국문 폴백
          String effLang = wantEn && !fallbackLang ? "en" : "ko";
          String contentId = "en".equals(effLang) ? enContentId : rs.getString("tour_content_id");
          var last = rs.getObject("last_departure_time", LocalTime.class);
          Map<String, Object> detail =
              tourApi.detail(contentId, effLang, rs.getString("intro_text"));
          // 사진 여러 장은 상세에서만 부른다. 폴백이면(TourAPI가 안 붙었으면) 사진도 건너뛴다 —
          // 어차피 못 받을 호출로 일일 카운터를 태우지 않는다.
          boolean useOk = rs.getBoolean("image_use_ok");
          List<String> extra = useOk && "TourAPI".equals(detail.get("source"))
              ? tourApi.images(contentId, effLang)
              : List.of();
          return new PoiDetailRes(rs.getLong("poi_id"), rs.getString("poi_name"),
              rs.getString("poi_kind"), rs.getString("tier"),
              effLang, fallbackLang, withPhotos(detail, useOk, extra),
              rs.getString("check_url"),
              last == null ? null : last.format(DateTimeFormatter.ofPattern("HH:mm")));
        }, poiId);
  }

  /**
   * detail에 사진 목록(images)을 얹는다. 첫 장은 대표 사진(firstimage)이라 목록 카드와
   * 같은 그림이 상세 첫 화면에 온다.
   *
   * **복사본에 담는 것이 핵심이다.** detail은 TourApiClient가 24시간 들고 있는 캐시
   * 객체라, 여기서 직접 put 하면 다음 요청부터 남의 값이 섞인다.
   *
   * imageUseOk=false는 목록과 같게 사진을 통째로 비운다 — 상세에만 새면 보류가 무의미하다.
   */
  private static Map<String, Object> withPhotos(Map<String, Object> detail,
      boolean imageUseOk, List<String> extra) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(detail);
    if (!imageUseOk) {
      out.put("imageUrl", null);
      out.put("images", List.of());
      return out;
    }
    List<String> photos = new ArrayList<>();
    Object first = detail.get("imageUrl");
    if (first != null) photos.add(first.toString());
    for (String u : extra) {
      if (!photos.contains(u)) photos.add(u);
    }
    out.put("images", List.copyOf(photos));
    return out;
  }
}
