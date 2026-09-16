package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.DayClass;
import com.example.geojeroserver.engine.Snapshot;
import com.example.geojeroserver.engine.SpotLayer;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.engine.Timetable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * 2026-09-14 v3(Figma 582:416): 3/4/5곳 칩을 없애고 **대표 코스 10개**를 카드로 보여준다 —
 * {@code featured=true}. 카드가 새로 말하는 것(제목·9경 번호·노선·하루 회차·휴일 운행)은 대표 코스가
 * 아니어도 전부 채운다. 순위 규칙은 서버가 런타임에 계산한다 — 코스를 다시 적재해도 순서가 따라온다.
 */
@RestController
public class CourseController {
  /** 버스 시각의 유일한 공식 원천. 코스의 모든 숫자가 여기서 나왔다(기준문서 §2). */
  private static final String SOURCE = "거제시 BIS 원문";
  /** 모든 코스의 출발·복귀 지점. 기획 결정으로 고현터미널 고정(기준문서 §6). */
  private static final String ORIGIN_NAME = "고현터미널";
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
  /** 대표 코스 수(사용자 결정 2026-09-14). */
  private static final int FEATURED_LIMIT = 10;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
   * 하루 회차 수 — 평일 시간표와 휴일 시간표 각각. BIS 표지의 「1일 N회」(기점발 회차)다.
   * 노선이 하나인 코스에만 붙는다 — 둘 이상이면 어느 노선의 횟수인지 말할 수 없고, 더하면
   * 실제로 타지 않는 숫자가 된다(노선을 섞어 평균 내지 않는 것과 같은 이유 — engine.md).
   */
  public record TripsPerDay(int weekday, int holiday) {}

  /**
   * ★ {@code busMinTotal} 이 이 서비스가 소유한 숫자다 — 구간 이동시간의 합이다.
   *
   * {@code approxTotalMin}(약 8시간 30분)은 출발부터 복귀까지의 **경과 시간**이라
   * 머무는 시간이 대부분이다(3-01: 버스 114분 + 머무는 401분). 얼마나 머물지는
   * 사용자가 정하는 것이라 2026-09-13에 화면에서 뺐다 — 컬럼과 필드는 남긴다.
   *
   * v3 카드(2026-09-14)가 더 말하는 것:
   *   title · intro     — V28 의 제목·소개(대표 10개에만 있다, 없으면 null)
   *   nineScenicNos     — 코스 스팟의 거제 9경 번호(pois.nine_scenic_no, 오름차순). ⚠️ nineScenicCount 는
   *                       팀원 적재값(V20)이라 매미성이 든 코스에서 하나 적다(CourseDataTest)
   *   busRoutes         — 탄 노선 번호, 구간·승차 순서대로 중복 없이
   *   tripsPerDay       — 노선이 하나일 때만(위 TripsPerDay)
   *   holidayService    — 모든 승차가 휴일 시간표에도 같은 시각으로 있는가
   */
  public record CourseCard(long courseId, String courseCode, int spotCount, int rank,
      int nineScenicCount, String name, String summary,
      String title, String intro,
      List<Integer> nineScenicNos, List<String> busRoutes,
      TripsPerDay tripsPerDay, boolean holidayService,
      String departAt, String returnAt, int approxTotalMin, String approxTotalText,
      int busMinTotal, String busTotalText,
      List<SpotBrief> spots) {}

  public record CoursesRes(Map<String, Integer> counts, List<CourseCard> courses) {}

  /** course_rides 한 줄 — 카드의 노선 목록과 휴일 운행 판정에 쓴다. 시각은 자정 기준 분. */
  private record RideRow(String routeNo, String boardStop, int boardMin,
      String alightStop, int alightMin) {}

  /** 평일 스냅샷과 휴일 스냅샷 한 쌍. 하루 회차 수·휴일 운행이 여기서 나온다. */
  private record Days(Snapshot weekday, Snapshot holiday) {}

  /** 코스 상세의 스팟 한 곳 — 체류 시각이 붙는다. */
  public record CourseStop(int seq, long poiId, String name, String shortName,
      String theme, Double lat, Double lng,
      String arriveAt, String leaveAt, Integer stayMin) {}

  /** 구간 안에서 실제로 탄 버스. SAME_STOP 구간은 이 목록이 빈다. */
  public record Ride(String routeNo, String boardStop, String boardAt, boolean boardEstimated,
      String alightStop, String alightAt, boolean alightEstimated) {}

  /**
   * 정류장 하나와 거기서 스팟까지의 직선거리(m).
   *
   * 버스가 서는 곳은 스팟이 아니라 정류장이다 — 화면이 「55번 · 10분」만 적으면 「10분 뒤 스팟 도착」으로 읽힌다.
   * 해금강은 내리는 정류장에서 직선 1.1km 다(2026-09-16 사용자 결정 — 디자인브리프 부록 H).
   * 값은 타는 곳 표(boarding_stops, V26·V34)에서 오고 {@code (from_poi_id, to_poi_id, route_no)} 로 찾는다 —
   * 코스 구간의 키와 모양이 같다. 거리는 **직선**이고 걷는 거리가 아니다(걷는 거리·시간은 어느 원문에도 없다 — 절대규칙 1).
   */
  public record StopWalk(String stop, Integer distanceM) {}

  /** 한 구간·노선의 타는 곳과 내리는 곳. 표의 한 줄에 둘 다 들어 있다. */
  private record StopPair(StopWalk board, StopWalk alight) {}

  /**
   * 코스의 한 구간. {@code estimated} 는 "이 구간 시각 중 하나라도 추정인가"다 —
   * 화면이 배지 하나만 그리면 되게 서버가 접어서 준다. 근거는 rides 안에 남아 있다.
   * {@code alight} 는 가는 곳이 스팟일 때만 있다 — 고현터미널로 돌아가는 마지막 구간은 null 이다.
   * {@code board} 는 출발 쪽이 스팟일 때만 있다 — 고현터미널에서 떠나는 첫 구간은 null 이다(터미널이 곧 정류장이라 0m 다).
   * 화면은 한 줄만 그린다: 내리는 곳이 있으면 그것, 없으면(마지막 구간) 타는 곳 — 그 구간과 스팟의 관계를 말한다.
   */
  public record Leg(int seq, String mode, Long fromPoiId, String fromName,
      Long toPoiId, String toName, String departAt, String arriveAt,
      int durationMin, int transfers, int transferWaitMin,
      boolean estimated, List<Ride> rides, StopWalk board, StopWalk alight) {}

  public record CourseDetail(long courseId, String courseCode, String name, String summary,
      String title, String intro,
      String theme, Integer spotCount, Integer nineScenicCount,
      String departAt, String returnAt, Integer totalMin, Integer approxTotalMin,
      String approxTotalText, int busMinTotal, String busTotalText,
      int legCount, int estimatedLegCount,
      String service, String baseDate, String source,
      String originName, String originStop,
      List<CourseStop> stops, List<Leg> legs) {}

  private final JdbcTemplate jdbc;
  private final SnapshotService snapshots;

  public CourseController(JdbcTemplate jdbc, SnapshotService snapshots) {
    this.jdbc = jdbc;
    this.snapshots = snapshots;
  }

  // ── 목록 ──────────────────────────────────────────────────────────────────

  /**
   * @param spotCount 3·4·5 로 거른다. 없으면 전량.
   * @param featured  true 면 대표 코스 10개만 — 9경이 많고, 버스 시간이 짧고, 스팟이 적은 순(같으면 코드 순).
   *                  spotCount 와 같이 오면 거른 뒤 고른다.
   */
  @GetMapping("/api/courses")
  public CoursesRes courses(@RequestParam(required = false) Integer spotCount,
      @RequestParam(required = false) Boolean featured) {
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
               course_name, summary, title, intro, depart_time, return_time, approx_total_min
        FROM courses
        WHERE course_code IS NOT NULL AND enabled""";
    var rows = spotCount == null
        ? jdbc.queryForList(sql + " ORDER BY spot_count, rank_no")
        : jdbc.queryForList(sql + " AND spot_count = ? ORDER BY spot_count, rank_no", spotCount);

    // 구간 이동시간 합을 한 번에 받아 둔다 — 카드마다 물으면 코스 수만큼 쿼리가 늘어난다.
    var busMin = busMinByCourse();

    // 대표 코스 — 9경이 많고(nine_scenic_count DESC), 버스가 짧고(ASC), 스팟이 적고(ASC), 코드 순.
    // SQL 이 아니라 여기서 고르는 이유: 버스 시간 합이 course_legs 에서 오고 이미 받아 뒀다.
    if (Boolean.TRUE.equals(featured)) {
      rows.sort(Comparator
          .comparingLong((Map<String, Object> r) -> num(r.get("nine_scenic_count"))).reversed()
          .thenComparingInt(r -> busMin.getOrDefault(num(r.get("course_id")), 0))
          .thenComparingLong(r -> num(r.get("spot_count")))
          .thenComparing(r -> (String) r.get("course_code")));
      rows = new ArrayList<>(rows.subList(0, Math.min(FEATURED_LIMIT, rows.size())));
    }

    // 노선·9경 번호도 한 번에 받아 course_id 로 묶는다(busMinByCourse 와 같은 방식).
    var rides = ridesByCourse();
    var nineNos = nineScenicNosByCourse();
    var days = weekdayAndHoliday();

    var cards = new ArrayList<CourseCard>();
    for (var c : rows) {
      long id = num(c.get("course_id"));
      int approx = (int) num(c.get("approx_total_min"));
      int bus = busMin.getOrDefault(id, 0);
      var courseRides = rides.getOrDefault(id, List.of());
      var routes = busRoutes(courseRides);
      cards.add(new CourseCard(id, (String) c.get("course_code"),
          (int) num(c.get("spot_count")), (int) num(c.get("rank_no")),
          (int) num(c.get("nine_scenic_count")),
          (String) c.get("course_name"), (String) c.get("summary"),
          (String) c.get("title"), (String) c.get("intro"),
          nineNos.getOrDefault(id, List.of()), routes,
          tripsPerDay(routes, days), holidayService(courseRides, days.holiday()),
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

  /** course_id → 탄 버스(구간·승차 순). 승차가 없는 코스(§3 검증 코스)는 키가 없다. */
  private Map<Long, List<RideRow>> ridesByCourse() {
    var out = new LinkedHashMap<Long, List<RideRow>>();
    jdbc.query("""
        SELECT l.course_id, r.route_no, r.board_stop, r.board_time, r.alight_stop, r.alight_time
        FROM course_rides r JOIN course_legs l ON l.leg_id = r.leg_id
        ORDER BY l.course_id, l.leg_seq, r.ride_seq""",
        rs -> {
          out.computeIfAbsent(rs.getLong("course_id"), k -> new ArrayList<>())
              .add(new RideRow(rs.getString("route_no"),
                  rs.getString("board_stop"), minutes(rs.getObject("board_time")),
                  rs.getString("alight_stop"), minutes(rs.getObject("alight_time"))));
        });
    return out;
  }

  /** course_id → 코스 스팟의 거제 9경 번호(오름차순). 9경이 없는 코스는 키가 없다. */
  private Map<Long, List<Integer>> nineScenicNosByCourse() {
    var out = new LinkedHashMap<Long, List<Integer>>();
    jdbc.query("""
        SELECT cp.course_id, p.nine_scenic_no
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE p.nine_scenic_no IS NOT NULL
        ORDER BY cp.course_id, p.nine_scenic_no""",
        rs -> {
          out.computeIfAbsent(rs.getLong("course_id"), k -> new ArrayList<>())
              .add(rs.getInt("nine_scenic_no"));
        });
    return out;
  }

  /**
   * 오늘(KST) 이후 첫 평일과 첫 휴일의 스냅샷. 어느 평일·어느 휴일이든 시간표는 같다 — trips 의
   * runs_weekday/runs_holiday 플래그로 갈리기 때문이다. 날짜를 박아두지 않는 이유는 시간표 버전이
   * 날짜로 유효기간을 갖기 때문이다(개편 뒤 옛 판을 읽게 된다). 공휴일은 holidays 표를 본다 —
   * 추석(2026-09-25 금)을 평일로 읽으면 휴일 시간표를 평일 회차라고 말하게 된다.
   */
  private Days weekdayAndHoliday() {
    Set<LocalDate> holidays = new HashSet<>(jdbc.query("SELECT holiday_date FROM holidays",
        (rs, i) -> rs.getObject("holiday_date", LocalDate.class)));
    LocalDate today = LocalDate.now(KST);
    return new Days(snapshots.forDate(firstOf(DayClass.WEEKDAY, today, holidays).toString()),
        snapshots.forDate(firstOf(DayClass.HOLIDAY, today, holidays).toString()));
  }

  private static LocalDate firstOf(DayClass want, LocalDate from, Set<LocalDate> holidays) {
    var d = from;
    while (TimeUtil.dayClassFor(d, holidays) != want) d = d.plusDays(1);
    return d;
  }

  /** 코스가 탄 노선 번호 — 구간·승차 순서대로, 중복 없이(55번을 두 번 타도 한 번). */
  private static List<String> busRoutes(List<RideRow> rides) {
    var out = new LinkedHashSet<String>();
    for (var r : rides) out.add(r.routeNo());
    return List.copyOf(out);
  }

  /**
   * 하루 회차 수 — 노선이 하나일 때만(TripsPerDay 주석). 기점발(direction 0) 회차 = BIS 표지의
   * 「1일 N회」. 55번은 6이다(기준문서 §2, 주말 동일).
   */
  private static TripsPerDay tripsPerDay(List<String> routes, Days days) {
    if (routes.size() != 1) return null;
    String route = routes.get(0);
    return new TripsPerDay((int) Timetable.countTripsOfRoute(days.weekday(), route, 0),
        (int) Timetable.countTripsOfRoute(days.holiday(), route, 0));
  }

  /**
   * 코스의 **모든** 승차가 휴일 시간표에도 있는가 — 같은 노선·같은 승차·같은 하차 시각으로.
   *
   * CourseTimetableConsistencyTest 가 평일 스냅샷에 하는 대조를 휴일 스냅샷에 되풀이하는 것이다.
   * 스팟 계층(SpotLayer)을 읽어야 도장포처럼 격자에 칸이 없는 정류장의 감싼 시각까지 같은 규칙으로
   * 나온다. 노선만 보면 틀린다 — 10·20번대는 평일/휴일 시간표가 갈라져 노선은 있어도 그 회차가
   * 없다(§2 요일 구조). 승차가 없는 코스는 "휴일에도 탄다"고 말할 근거가 없으니 거짓이다.
   */
  private static boolean holidayService(List<RideRow> rides, Snapshot holiday) {
    if (rides.isEmpty()) return false;
    for (var r : rides) {
      // 그 노선만 남긴다 — 회차마다 경로 문장을 다시 읽는 비용을 코스 수 × 노선 수로 묶는다.
      var onRoute = Timetable.subsetByRoute(holiday, r.routeNo());
      boolean found = SpotLayer.rides(onRoute, r.boardStop(), r.alightStop()).stream()
          .anyMatch(x -> x.departMin() == r.boardMin() && x.arriveMin() == r.alightMin());
      if (!found) return false;
    }
    return true;
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

  /** 고현터미널 poi. 코스 구간은 터미널을 NULL 로 적지만 타는 곳 표는 그 행의 id 로 적는다. */
  private Long terminalPoiId() {
    var ids = jdbc.queryForList(
        "SELECT poi_id FROM pois WHERE poi_kind = 'TERMINAL' ORDER BY poi_id LIMIT 1", Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private static String stopKey(Long fromPoi, Long toPoi, String routeNo) {
    return fromPoi + ">" + toPoi + ">" + routeNo;
  }

  /**
   * 이 코스가 지나는 (출발, 가는 곳, 노선)마다 내리는 정류장. 구간마다 쿼리를 쏘지 않게 한 번에 받는다.
   * 표에 그 조합이 없으면 그냥 없다 — 가까운 정류장을 추측으로 고르지 않는다(절대규칙 1).
   */
  private Map<String, StopPair> stopsOf(long courseId, Long terminal) {
    var out = new HashMap<String, StopPair>();
    jdbc.query("""
        SELECT b.from_poi_id, b.to_poi_id, b.route_no,
               b.stop_name, b.distance_m, b.alight_stop_name, b.alight_distance_m
        FROM boarding_stops b
        WHERE b.status = 'RESOLVED'
          AND EXISTS (SELECT 1 FROM course_legs l JOIN course_rides r ON r.leg_id = l.leg_id
                      WHERE l.course_id = ?
                        AND COALESCE(l.from_poi_id, ?) = b.from_poi_id
                        AND COALESCE(l.to_poi_id, ?) = b.to_poi_id
                        AND r.route_no = b.route_no)""",
        rs -> {
          out.put(stopKey(rs.getLong("from_poi_id"), rs.getLong("to_poi_id"), rs.getString("route_no")),
              new StopPair(
                  new StopWalk(rs.getString("stop_name"), (Integer) rs.getObject("distance_m")),
                  new StopWalk(rs.getString("alight_stop_name"), (Integer) rs.getObject("alight_distance_m"))));
        }, courseId, terminal, terminal);
    return out;
  }

  @GetMapping("/api/courses/{courseId}")
  public CourseDetail course(@PathVariable long courseId) {
    var rows = jdbc.queryForList("""
        SELECT course_id, course_code, course_name, summary, title, intro, theme, spot_count,
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

    Long terminal = terminalPoiId();
    var stopPairs = stopsOf(courseId, terminal);

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
      // 노선은 **마지막으로 탄 버스**다. 환승은 코스에 없지만(기준문서 §6) 있어도 내리는 것은 마지막 버스다.
      StopPair pair = rides.isEmpty() ? null
          : stopPairs.get(stopKey(l.get("from_poi_id") == null ? terminal : num(l.get("from_poi_id")),
              l.get("to_poi_id") == null ? terminal : num(l.get("to_poi_id")),
              rides.get(rides.size() - 1).routeNo()));
      // 내리는 곳은 가는 곳이 스팟일 때만, 타는 곳은 출발 쪽이 스팟일 때만 — 고현터미널은 그 자체가 정류장이라 0m 다.
      StopWalk alight = pair == null || l.get("to_poi_id") == null ? null : pair.alight();
      StopWalk board = pair == null || l.get("from_poi_id") == null ? null : pair.board();
      legs.add(new Leg(
          (int) num(l.get("leg_seq")), (String) l.get("mode"),
          l.get("from_poi_id") == null ? null : num(l.get("from_poi_id")),
          l.get("from_poi_id") == null ? ORIGIN_NAME : (String) l.get("from_name"),
          l.get("to_poi_id") == null ? null : num(l.get("to_poi_id")),
          l.get("to_poi_id") == null ? ORIGIN_NAME : (String) l.get("to_name"),
          hm(l.get("depart_time")), hm(l.get("arrive_time")),
          (int) num(l.get("duration_min")), (int) num(l.get("transfers")),
          (int) num(l.get("transfer_wait_min")), est, rides, board, alight));
    }

    Integer approx = (Integer) c.get("approx_total_min");
    // 구간 이동시간 합. 이미 만든 legs 를 더하므로 쿼리를 더 쏘지 않는다.
    int busMin = legs.stream().mapToInt(Leg::durationMin).sum();
    return new CourseDetail(courseId, (String) c.get("course_code"),
        (String) c.get("course_name"), (String) c.get("summary"),
        (String) c.get("title"), (String) c.get("intro"), (String) c.get("theme"),
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

  /** time 컬럼 → 자정 기준 분. 시간표 엔진이 쓰는 단위다(engine.md). */
  private static int minutes(Object time) {
    LocalTime t = ((java.sql.Time) time).toLocalTime();
    return t.getHour() * 60 + t.getMinute();
  }

  private static long num(Object o) {
    return ((Number) o).longValue();
  }

  private static Double dbl(Object o) {
    return o == null ? null : ((Number) o).doubleValue();
  }
}
