package com.example.geojeroserver.api;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.example.geojeroserver.tour.TourApiClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoiController {
  // lat·lng는 좌표 미확보 POI가 있어 nullable(Double). 0.0으로 떨어지면 지도에 유령 핀이 찍힌다.
  public record PoiListItem(long poiId, String name, String kind, String tier,
                            boolean hasEnglish, Double lat, Double lng) {}
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
  public PoisRes list() {
    return new PoisRes(jdbc.query("""
        SELECT p.poi_id, p.poi_name, p.poi_kind, p.tier,
               EXISTS(SELECT 1 FROM poi_i18n i
                      WHERE i.poi_id = p.poi_id AND i.lang = 'EN'
                        AND i.matched_by = 'HUMAN') AS has_en,
               p.lat, p.lng
        FROM pois p ORDER BY p.poi_id""",
        (rs, i) -> new PoiListItem(rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getString(4), rs.getBoolean(5),
            toDouble(rs.getBigDecimal(6)), toDouble(rs.getBigDecimal(7)))));
  }

  @GetMapping("/api/pois/{poiId}")
  public PoiDetailRes detail(@PathVariable long poiId,
      @RequestParam(defaultValue = "ko") String lang) {
    return jdbc.queryForObject("""
        SELECT p.poi_id, p.poi_name, p.poi_kind, p.tier, p.intro_text, p.check_url,
               p.last_departure_time, p.tour_content_id,
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
          return new PoiDetailRes(rs.getLong("poi_id"), rs.getString("poi_name"),
              rs.getString("poi_kind"), rs.getString("tier"),
              effLang, fallbackLang, detail,
              rs.getString("check_url"),
              last == null ? null : last.format(DateTimeFormatter.ofPattern("HH:mm")));
        }, poiId);
  }
}
