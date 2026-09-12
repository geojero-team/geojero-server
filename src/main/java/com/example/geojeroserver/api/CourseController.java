package com.example.geojeroserver.api;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 코스 API — 02-2 화면(Figma 442:748)의 코스 추천 → 지도 → 코스 상세를 이 둘로 그린다.
 *
 * 2026-09-12에 DB 기반으로 다시 썼다. 전에는 §3 검증 코스 3종을 상수로 내려줄 뿐이었고
 * 구간 정보가 없었다(course_pois 0행). 지금은 V17이 적재한 추천 코스를 legs·rides까지 내려준다.
 *
 * **목록에는 추천 코스만 담는다.** §3 검증 코스 3종은 화면에 뜨지 않지만
 * saved_trips.course_id 가 가리킬 수 있어 상세 조회는 된다(구간 없이 이름·요약만).
 */
@RestController
public class CourseController {
  /** 버스 시각의 유일한 공식 원천. 코스의 모든 숫자가 여기서 나왔다(기준문서 §2). */
  private static final String SOURCE = "거제시 BIS 원문";
  /** 모든 코스의 출발·복귀 지점. 기획 결정으로 고현터미널 고정(기준문서 §6). */
  private static final String ORIGIN_NAME = "고현터미널";
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

  public record CourseDto(long courseId, String name, String theme, String summary) {}

  /** §3 검증 코스 3종 — 확정 데이터 기반 상수. DB 동기화는 CourseSeeder(saved_trips FK 원천). */
  public static final List<CourseDto> COURSES = List.of(
      new CourseDto(1, "부산발 당일치기", "ISLAND",
          "사상 07:00 → 바람의언덕·유람선 → 막차 복귀 → 부산행 21:10"),
      new CourseDto(2, "서울발 무박 일출", "NATURE",
          "서울남부 23:30 → 첫차 06:25 → 일출 → 상행 22:00"),
      new CourseDto(3, "외도 풀코스", "ISLAND",
          "도장포 막배 15:30 → 18:20 복귀 → 버스 연결"));

  // ── 응답 모양 ──────────────────────────────────────────────────────────────

  /** 카드의 원형 썸네일 하나 + 지도 핀 하나. */
  public record SpotBrief(int seq, long poiId, String name, String shortName,
      String theme, Double lat, Double lng) {}

  /**
   * ★ {@code busMinTotal} 이 이 서비스가 소유한 숫자다 — 구간 이동시간의 합이다.
   *
   * {@code approxTotalMin}(약 8시간 30분)은 출발부터 복귀까지의 **경과 시간**이라
   * 머무는 시간이 대부분이다(3-01: 버스 114분 + 머무는 401분). 얼마나 머물지는
   * 사용자가 정하는 것이라 2026-09-13에 화면에서 뺐다 — 컬럼과 필드는 남긴다.
   */
  public record CourseCard(long courseId, String courseCode, int spotCount, int rank,
      int nineScenicCount, String name, String summary,
      String departAt, String returnAt, int approxTotalMin, String approxTotalText,
      int busMinTotal, String busTotalText,
      List<SpotBrief> spots) {}

  public record CoursesRes(Map<String, Integer> counts, List<CourseCard> courses) {}

  /** 코스 상세의 스팟 한 곳 — 체류 시각이 붙는다. */
  public record CourseStop(int seq, long poiId, String name, String shortName,
      String theme, Double lat, Double lng,
      String arriveAt, String leaveAt, Integer stayMin) {}

  /** 구간 안에서 실제로 탄 버스. SAME_STOP 구간은 이 목록이 빈다. */
  public record Ride(String routeNo, String boardStop, String boardAt, boolean boardEstimated,
      String alightStop, String alightAt, boolean alightEstimated) {}

  /**
   * 코스의 한 구간. {@code estimated} 는 "이 구간 시각 중 하나라도 추정인가"다 —
   * 화면이 배지 하나만 그리면 되게 서버가 접어서 준다. 근거는 rides 안에 남아 있다.
   */
  public record Leg(int seq, String mode, Long fromPoiId, String fromName,
      Long toPoiId, String toName, String departAt, String arriveAt,
      int durationMin, int transfers, int transferWaitMin,
      boolean estimated, List<Ride> rides) {}

  public record CourseDetail(long courseId, String courseCode, String name, String summary,
      String theme, Integer spotCount, Integer nineScenicCount,
      String departAt, String returnAt, Integer totalMin, Integer approxTotalMin,
      String approxTotalText, int busMinTotal, String busTotalText,
      int legCount, int estimatedLegCount,
      String service, String baseDate, String source,
      String originName, String originStop,
      List<CourseStop> stops, List<Leg> legs) {}

  private final JdbcTemplate jdbc;

  public CourseController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── 목록 ──────────────────────────────────────────────────────────────────

  @GetMapping("/api/courses")
  public CoursesRes courses(@RequestParam(required = false) Integer spotCount) {
    // 칩은 코스가 0개여도 개수를 보여줘야 하므로 필터와 별개로 전량을 센다.
    var counts = new LinkedHashMap<String, Integer>();
    for (int n : new int[] {3, 4, 5}) {
      counts.put(String.valueOf(n), jdbc.queryForObject("""
          SELECT count(*) FROM courses
          WHERE course_code IS NOT NULL AND enabled AND spot_count = ?""",
          Integer.class, n));
    }

    // 파라미터를 `? IS NULL` 로 비교하면 Postgres가 타입을 못 정한다(500). SQL을 갈라 만든다.
    String sql = """
        SELECT course_id, course_code, spot_count, rank_no, nine_scenic_count,
               course_name, summary, depart_time, return_time, approx_total_min
        FROM courses
        WHERE course_code IS NOT NULL AND enabled""";
    var rows = spotCount == null
        ? jdbc.queryForList(sql + " ORDER BY spot_count, rank_no")
        : jdbc.queryForList(sql + " AND spot_count = ? ORDER BY spot_count, rank_no", spotCount);

    // 구간 이동시간 합을 한 번에 받아 둔다 — 카드마다 물으면 코스 수만큼 쿼리가 늘어난다.
    var busMin = busMinByCourse();

    var cards = new ArrayList<CourseCard>();
    for (var c : rows) {
      long id = num(c.get("course_id"));
      int approx = (int) num(c.get("approx_total_min"));
      int bus = busMin.getOrDefault(id, 0);
      cards.add(new CourseCard(id, (String) c.get("course_code"),
          (int) num(c.get("spot_count")), (int) num(c.get("rank_no")),
          (int) num(c.get("nine_scenic_count")),
          (String) c.get("course_name"), (String) c.get("summary"),
          hm(c.get("depart_time")), hm(c.get("return_time")),
          approx, approxText(approx),
          bus, approxText(bus), spotBriefs(id)));
    }
    return new CoursesRes(counts, cards);
  }

  /** course_id → 구간 이동시간 합(분). 구간이 없는 코스는 키가 없다. */
  private Map<Long, Integer> busMinByCourse() {
    var out = new LinkedHashMap<Long, Integer>();
    jdbc.query("""
        SELECT course_id, COALESCE(sum(duration_min), 0) AS bus_min
        FROM course_legs GROUP BY course_id""",
        rs -> {
          out.put(rs.getLong("course_id"), rs.getInt("bus_min"));
        });
    return out;
  }

  private List<SpotBrief> spotBriefs(long courseId) {
    return jdbc.query("""
        SELECT cp.poi_seq, p.poi_id, p.poi_name, p.short_name, p.theme, p.lat, p.lng
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE cp.course_id = ? ORDER BY cp.poi_seq""",
        (rs, i) -> new SpotBrief(rs.getInt("poi_seq"), rs.getLong("poi_id"),
            rs.getString("poi_name"), rs.getString("short_name"), rs.getString("theme"),
            dbl(rs.getObject("lat")), dbl(rs.getObject("lng"))),
        courseId);
  }

  // ── 상세 ──────────────────────────────────────────────────────────────────

  @GetMapping("/api/courses/{courseId}")
  public CourseDetail course(@PathVariable long courseId) {
    var rows = jdbc.queryForList("""
        SELECT course_id, course_code, course_name, summary, theme, spot_count,
               nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
               service, base_date, origin_stop
        FROM courses WHERE course_id = ? AND enabled""", courseId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 코스");
    }
    var c = rows.get(0);

    var stops = jdbc.query("""
        SELECT cp.poi_seq, p.poi_id, p.poi_name, p.short_name, p.theme, p.lat, p.lng,
               cp.arrive_time, cp.leave_time, cp.stay_min
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE cp.course_id = ? ORDER BY cp.poi_seq""",
        (rs, i) -> new CourseStop(rs.getInt("poi_seq"), rs.getLong("poi_id"),
            rs.getString("poi_name"), rs.getString("short_name"), rs.getString("theme"),
            dbl(rs.getObject("lat")), dbl(rs.getObject("lng")),
            hm(rs.getObject("arrive_time")), hm(rs.getObject("leave_time")),
            (Integer) rs.getObject("stay_min")),
        courseId);

    var legs = new ArrayList<Leg>();
    for (var l : jdbc.queryForList("""
        SELECT l.leg_id, l.leg_seq, l.mode, l.from_poi_id, l.to_poi_id,
               l.depart_time, l.arrive_time, l.duration_min, l.transfers, l.transfer_wait_min,
               COALESCE(pf.short_name, pf.poi_name) AS from_name,
               COALESCE(pt.short_name, pt.poi_name) AS to_name
        FROM course_legs l
        LEFT JOIN pois pf ON pf.poi_id = l.from_poi_id
        LEFT JOIN pois pt ON pt.poi_id = l.to_poi_id
        WHERE l.course_id = ? ORDER BY l.leg_seq""", courseId)) {
      long legId = num(l.get("leg_id"));
      var rides = jdbc.query("""
          SELECT route_no, board_stop, board_time, board_estimated,
                 alight_stop, alight_time, alight_estimated
          FROM course_rides WHERE leg_id = ? ORDER BY ride_seq""",
          (rs, i) -> new Ride(rs.getString("route_no"), rs.getString("board_stop"),
              hm(rs.getObject("board_time")), rs.getBoolean("board_estimated"),
              rs.getString("alight_stop"), hm(rs.getObject("alight_time")),
              rs.getBoolean("alight_estimated")),
          legId);
      boolean est = rides.stream().anyMatch(r -> r.boardEstimated() || r.alightEstimated());
      legs.add(new Leg(
          (int) num(l.get("leg_seq")), (String) l.get("mode"),
          l.get("from_poi_id") == null ? null : num(l.get("from_poi_id")),
          l.get("from_poi_id") == null ? ORIGIN_NAME : (String) l.get("from_name"),
          l.get("to_poi_id") == null ? null : num(l.get("to_poi_id")),
          l.get("to_poi_id") == null ? ORIGIN_NAME : (String) l.get("to_name"),
          hm(l.get("depart_time")), hm(l.get("arrive_time")),
          (int) num(l.get("duration_min")), (int) num(l.get("transfers")),
          (int) num(l.get("transfer_wait_min")), est, rides));
    }

    Integer approx = (Integer) c.get("approx_total_min");
    // 구간 이동시간 합. 이미 만든 legs 를 더하므로 쿼리를 더 쏘지 않는다.
    int busMin = legs.stream().mapToInt(Leg::durationMin).sum();
    return new CourseDetail(courseId, (String) c.get("course_code"),
        (String) c.get("course_name"), (String) c.get("summary"), (String) c.get("theme"),
        (Integer) c.get("spot_count"), (Integer) c.get("nine_scenic_count"),
        hm(c.get("depart_time")), hm(c.get("return_time")),
        (Integer) c.get("total_min"), approx,
        approx == null ? null : approxText(approx),
        busMin, approxText(busMin),
        legs.size(), (int) legs.stream().filter(Leg::estimated).count(),
        (String) c.get("service"),
        c.get("base_date") == null ? null : c.get("base_date").toString(),
        c.get("course_code") == null ? null : SOURCE,
        c.get("course_code") == null ? null : ORIGIN_NAME,
        (String) c.get("origin_stop"),
        stops, legs);
  }

  // 코스 존재 확인(hasCourse)은 SavedTripController가 DB에서 직접 한다 —
  // 코스가 상수에서 DB로 옮겨왔고, 상수만 보면 추천 코스 저장이 전부 400이 된다.

  // ── 표시 형식 ─────────────────────────────────────────────────────────────

  /**
   * "약 8시간 30분" / "약 10시간". 정시간이면 분을 붙이지 않는다 —
   * "약 10시간 0분"은 사람이 쓰는 말이 아니다.
   */
  static String approxText(int min) {
    int h = min / 60;
    int m = min % 60;
    return m == 0 ? "약 " + h + "시간" : "약 " + h + "시간 " + m + "분";
  }

  private static String hm(Object time) {
    return time == null ? null : ((java.sql.Time) time).toLocalTime().format(HM);
  }

  private static long num(Object o) {
    return ((Number) o).longValue();
  }

  private static Double dbl(Object o) {
    return o == null ? null : ((Number) o).doubleValue();
  }
}
