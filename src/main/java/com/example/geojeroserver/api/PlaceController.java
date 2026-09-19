package com.example.geojeroserver.api;

import com.example.geojeroserver.exception.BusinessException;
import com.example.geojeroserver.exception.ErrorCode;
import com.example.geojeroserver.tour.TourApiClient;
import com.example.geojeroserver.tour.TourApiClient.PlaceInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 맛집 12 · 숙소 7 (V40, 2026-09-19 사용자 결정 — 기준문서 §6 「맛집 · 숙소」). 화면은 스팟 탭의 「맛집」 「숙소」 칩과 `/places/:placeId`.
 *
 * 우리 DB 에는 고른 곳 · 순서 · 등급 · 예약 주소만 있고, 소개 · 사진 · 영업시간 · 체크인은 TourAPI 런타임 호출이다(24h 캐시 —
 * 스팟과 같은 방식, 로컬 저장 안 함). TourAPI 가 실패해도 목록 · 상세는 이름 · 종류 · 가까운 스팟 · 예약 주소로 선다(detail FALLBACK).
 *
 * 가까운 스팟: 화면 스팟(theme 있음) 중 **배로만 가는 곳을 뺀** 곳까지의 직선거리(TourAPI 좌표). 배로만 가는 곳 —
 * 외도보타니아 · 공곶이·내도 · 지심도(정류장 없이 선착장만) — 은 직선이 바다를 건너 뜻이 없다.
 * 목록 카드는 가장 가까운 한 곳, 상세는 5km 안에서 가까운 순으로 최대 3곳.
 */
@RestController
public class PlaceController {
  static final int NEAR_MAX_M = 5000;
  static final int NEAR_MAX_COUNT = 3;

  public record NearSpot(long poiId, String shortName, int distanceM, double lat, double lng) {}

  /** lat · lng — 홈 지도의 숙소 · 맛집 핀(2026-09-19). 우리 DB 값이라 TourAPI 가 실패해도 있다. */
  public record PlaceListItem(long placeId, String kind, String name, String category, String imageUrl,
                              Integer grade, String restDay, NearSpot nearSpot, double lat, double lng) {}

  public record PlacesRes(List<PlaceListItem> places) {}

  public record PlaceDetailRes(long placeId, String kind, String name, String category, Integer grade,
                               double lat, double lng, String bookingUrl, List<NearSpot> nearSpots,
                               Map<String, Object> detail) {}

  /** fallbackImages — TourAPI 가 사진을 0장 줄 때 쓰는 사진 주소(V41 — 지금은 한화 하나, 운영 키로 영문 사진이 안 와서). */
  private record Place(long contentId, String kind, String name, double lat, double lng, String category,
                       Integer grade, String bookingUrl, String engContentId, List<String> fallbackImages) {
    String contentTypeId() {
      return "FOOD".equals(kind) ? "39" : "32";
    }
  }

  private record Spot(long poiId, String shortName, double lat, double lng) {}

  private final JdbcTemplate jdbc;
  private final TourApiClient tourApi;

  public PlaceController(JdbcTemplate jdbc, TourApiClient tourApi) {
    this.jdbc = jdbc;
    this.tourApi = tourApi;
  }

  private static final String PLACE_COLUMNS =
      "content_id, kind, name, lat, lng, category, grade, booking_url, eng_content_id, fallback_image_urls";

  private static Place place(java.sql.ResultSet rs) throws java.sql.SQLException {
    Object eng = rs.getObject("eng_content_id");
    java.sql.Array fallback = rs.getArray("fallback_image_urls");
    return new Place(rs.getLong("content_id"), rs.getString("kind"), rs.getString("name"),
        rs.getBigDecimal("lat").doubleValue(), rs.getBigDecimal("lng").doubleValue(),
        rs.getString("category"), rs.getObject("grade", Integer.class), rs.getString("booking_url"),
        eng == null ? null : eng.toString(),
        fallback == null ? List.of() : List.of((String[]) fallback.getArray()));
  }

  /** TourAPI 사진(대표 + 추가). 0장이면 DB 에 둔 사진 주소(V41). */
  private List<String> images(Place p, PlaceInfo info) {
    List<String> images = new ArrayList<>();
    if (info.firstImage() != null) images.add(info.firstImage());
    for (String u : tourApi.placeImages(String.valueOf(p.contentId()), p.engContentId())) {
      if (!images.contains(u)) images.add(u);
    }
    return images.isEmpty() ? p.fallbackImages() : images;
  }

  @GetMapping("/api/places")
  public PlacesRes list(@RequestParam String kind) {
    if (!"FOOD".equals(kind) && !"STAY".equals(kind)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    var places = jdbc.query("SELECT " + PLACE_COLUMNS + " FROM places WHERE kind = ? ORDER BY sort_order",
        (rs, i) -> place(rs), kind);
    var spots = spots();

    // TourAPI 는 곳마다 부른다 — 동시에 던진다(PoiController 와 같은 방식). 캐시(24h)가 찬 뒤에는 호출이 없다.
    List<PlaceListItem> out = new ArrayList<>(places.size());
    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = places.stream().map(p -> exec.submit(() -> listItem(p, spots))).toList();
      for (int i = 0; i < places.size(); i++) {
        try {
          out.add(tasks.get(i).get());
        } catch (Exception e) {
          out.add(listItem(places.get(i), null, null, spots));
        }
      }
    }
    return new PlacesRes(out);
  }

  private PlaceListItem listItem(Place p, List<Spot> spots) {
    PlaceInfo info = tourApi.placeInfo(String.valueOf(p.contentId()), p.contentTypeId());
    String image = info == null ? null : info.firstImage();
    // 대표 사진이 없으면 추가 사진 첫 장(한화 — 국문 0장이라 영문, 그것도 0장이면 DB 의 주소). 목록에서 추가 사진을 부르는 건 이 경우뿐이다.
    if (info != null && image == null) {
      var more = images(p, info);
      image = more.isEmpty() ? null : more.getFirst();
    }
    return listItem(p, info, image, spots);
  }

  private static PlaceListItem listItem(Place p, PlaceInfo info, String image, List<Spot> spots) {
    boolean food = "FOOD".equals(p.kind());
    Map<String, String> intro = info == null ? Map.of() : info.intro();
    var near = nearest(p, spots);
    return new PlaceListItem(p.contentId(), p.kind(), p.name(),
        food ? intro.get("firstmenu") : p.category(), image, p.grade(),
        food ? intro.get("restdatefood") : null, near.isEmpty() ? null : near.getFirst(), p.lat(), p.lng());
  }

  @GetMapping("/api/places/{placeId}")
  public PlaceDetailRes detail(@PathVariable long placeId) {
    var found = jdbc.query("SELECT " + PLACE_COLUMNS + " FROM places WHERE content_id = ?",
        (rs, i) -> place(rs), placeId);
    if (found.isEmpty()) throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
    Place p = found.getFirst();
    boolean food = "FOOD".equals(p.kind());

    PlaceInfo info = tourApi.placeInfo(String.valueOf(p.contentId()), p.contentTypeId());
    Map<String, Object> detail = new LinkedHashMap<>();
    if (info == null) {
      // 이유 없는 빈칸 금지 — 이유와 시각을 준다(스팟 상세 폴백과 같은 모양)
      detail.put("source", "FALLBACK");
      detail.put("reason", "관광정보 확인 실패");
      detail.put("checkedAt", Instant.now().toString());
    } else {
      Map<String, String> intro = info.intro();
      detail.put("source", "TourAPI");
      detail.put("address", info.address());
      detail.put("images", images(p, info));
      // 소개문(overview)은 싣지 않는다 — 숙소는 호텔 자기 홍보 글이고, 맛집은 네이버 · 카카오도 첫 화면에 긴 소개글을
      // 두지 않는다(원문을 고칠 수 없어 요약도 못 한다, 2026-09-19 사용자)
      if (food) {
        detail.put("openTime", intro.get("opentimefood"));
        detail.put("restDay", intro.get("restdatefood"));
      } else {
        detail.put("checkIn", intro.get("checkintime"));
        detail.put("checkOut", intro.get("checkouttime"));
        detail.put("facilities", intro.get("subfacility"));
      }
    }
    var near = nearest(p, spots()).stream().filter(s -> s.distanceM() <= NEAR_MAX_M).limit(NEAR_MAX_COUNT).toList();
    return new PlaceDetailRes(p.contentId(), p.kind(), p.name(),
        food ? (info == null ? null : info.intro().get("firstmenu")) : p.category(), p.grade(),
        p.lat(), p.lng(), p.bookingUrl(), near, detail);
  }

  /** 가까운 스팟 후보 — 화면 스팟 중 배로만 가는 곳(정류장이 없고 선착장이 있는 곳)을 뺀 곳. */
  private List<Spot> spots() {
    return jdbc.query("""
        SELECT p.poi_id, p.short_name, p.lat, p.lng
          FROM pois p
         WHERE p.theme IS NOT NULL AND p.lat IS NOT NULL AND p.lng IS NOT NULL
           AND NOT (p.alight_label IS NULL
                    AND (EXISTS (SELECT 1 FROM ferry_links l WHERE l.poi_id = p.poi_id AND l.relation = 'DESTINATION')
                         OR EXISTS (SELECT 1 FROM shuttle_docks s WHERE s.poi_id = p.poi_id)))
        """,
        (rs, i) -> new Spot(rs.getLong("poi_id"), rs.getString("short_name"),
            rs.getBigDecimal("lat").doubleValue(), rs.getBigDecimal("lng").doubleValue()));
  }

  /** 가까운 순 전체. */
  private static List<NearSpot> nearest(Place p, List<Spot> spots) {
    return spots.stream()
        .map(s -> new NearSpot(s.poiId(), s.shortName(), meters(p.lat(), p.lng(), s.lat(), s.lng()), s.lat(), s.lng()))
        .sorted(Comparator.comparingInt(NearSpot::distanceM))
        .toList();
  }

  /** 두 좌표 사이 직선거리(하버사인, 미터 반올림). */
  static int meters(double lat1, double lng1, double lat2, double lng2) {
    double r = 6_371_000;
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double h = Math.pow(Math.sin((p2 - p1) / 2), 2)
        + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
    return (int) Math.round(2 * r * Math.asin(Math.sqrt(h)));
  }
}
