package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.SpotLayer;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.engine.Timetable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 스팟 시간표 — 02-2의 「스팟 시간표」 3화면(453:210 · 453:288 · 453:415)과 시간표 탭.
 *
 * 정류소 기준 조회(`/api/stops/{stop}/departures`)와 다른 점은 **스팟 이름으로 묻는다**는 것이다.
 * 화면이 "학동몽돌해변"을 누르는데 시간표에는 "학동"만 있어서, 그 사이를 pois 가 잇는다
 * (V18의 timetable_stop / alight_label).
 *
 * ★ 내리는 정류장과 시간표를 읽는 정류장이 다를 수 있다 — 조선해양문화관·씨월드는
 * 신촌에서 내리지만 시간표에 신촌 칸이 없어 지세포 시각을 쓴다. 숨기지 않고
 * boardStopDiffers 로 알린다. 숨기면 "지세포 시간표"를 "신촌 시간표"라고 거짓말하는 것이다.
 *
 * ★ 2026-09-13: 시각은 **스팟 계층(SpotLayer)** 에서 읽는다. 원문 격자에는 도장포(55번)·대계·
 * 대금교차로·포로수용소·식물원·옥포대첩기념공원·맹종죽테마파크 칸이 없어, 격자만 읽으면
 * 코스(55번 · 12분)와 시간표(운행 없음)가 서로 다른 말을 했다. 스팟 계층은 팀원 파이프라인의
 * 규칙(경로 문장 속 시각 · 앞뒤 정류장으로 감싼 시각)을 그대로 적용하고, 감싼 값은
 * departures[].estimated 로 알린다. 빈 결과의 이유(시각 미상)는 여전히 격자에서 본다.
 */
@RestController
public class SpotTimetableController {
  /** 모든 코스의 출발·복귀 지점. to 를 주지 않으면 여기가 목적지다. */
  private static final String ORIGIN_STOP = "고현";
  private static final String ORIGIN_NAME = "고현터미널";
  private static final String SOURCE = "거제시 BIS 원문";

  public record Endpoint(Long poiId, String stop, String name) {}

  /**
   * estimated 가 참이면 승차 또는 하차 시각이 앞뒤 정류장으로 감싼 값이다(SpotLayer 규칙 3·7).
   * departEstimated 는 **depart(승차 시각)** 가 감싼 값인지다 — 고현 → 바람의언덕은 도착만 추정이라
   * estimated 는 참이어도 06:25 출발은 원문 칸 그대로다. 화면이 시각에 추정 표시를 붙일 때 이것을 본다(2026-09-14).
   */
  public record Departure(String routeNo, String depart, String arrive, Integer durationMin,
      boolean estimated, boolean departEstimated) {}

  /**
   * 노선별 소요시간. 섞어 평균을 내면 실제로 운행하지 않는 값이 나온다(engine.md).
   *
   * {@code durationMin} 은 **최댓값**(늦게 닿는 쪽)이다 — 화면이 이 값을 써야 사용자가
   * 버스를 놓치지 않는다. 같은 노선·방향인데도 흔들리는 이유는 67·67-1이 50번대와 60번대
   * 시트에 같은 회차로 두 번 실려 2~5분 다르기 때문이다(팀원 spot_times warnings).
   */
  public record RouteSummary(String routeNo, int count, Integer durationMin,
      Integer durationMinLow, boolean durationVaries) {}

  public record SpotDeparturesRes(long poiId, String name, String shortName,
      String boardStop, String alightLabel, boolean boardStopDiffers,
      Endpoint to, String date, String dayClass,
      List<Departure> departures, int count,
      String firstDeparture, String lastDeparture,
      Departure next, List<RouteSummary> byRoute,
      String emptyReason, List<String> unknownTimeRoutes,
      String source, String baseDate, Boarding boarding) {}

  // ── 타는 곳 (2026-09-14, V25·V26) ──────────────────────────────────────────

  /** 이 구간의 출발 쪽. kind 는 SPOT 또는 TERMINAL(고현터미널). 거리는 이 좌표에서 잰다. */
  public record BoardingPlace(String name, String kind, double lat, double lng) {}

  /** 대표 핀 — 같은 정류장을 쓰는 노선을 묶는다. routes 는 byRoute 순서. */
  public record BoardingStop(String nodeId, String name, double lat, double lng, int distanceM,
      List<String> routes) {}

  /**
   * 대표 핀과 다른 정류장에서 타는 편. mainNodeId 가 있으면 그 노선 대표 핀의 건너편(gapM),
   * 없으면 그 노선은 편마다 타는 쪽이 달라 대표 핀이 없다(김영삼 생가 32번).
   */
  public record BoardingException(String routeNo, String depart, String nodeId, String name,
      double lat, double lng, int distanceM, String mainNodeId, Integer gapM) {}

  /** 핀이 없는 노선. reason: TOO_FAR · NO_STOP_NAME · WRONG_DIRECTION · NOT_COLLECTED(표에 그 구간·노선이 없다). */
  public record Unresolved(String routeNo, String reason) {}

  public record Boarding(BoardingPlace from, List<BoardingStop> stops,
      List<BoardingException> exceptions, List<Unresolved> unresolved, String source) {}

  private final JdbcTemplate jdbc;
  private final SnapshotService snapshots;

  public SpotTimetableController(JdbcTemplate jdbc, SnapshotService snapshots) {
    this.jdbc = jdbc;
    this.snapshots = snapshots;
  }

  /**
   * @param from  "origin" 이면 고현터미널 → 스팟 방향으로 뒤집는다(453:415 "타는 곳이 고현터미널로 바뀜").
   * @param toPoiId 목적지를 스팟으로 줄 때. 없으면 고현터미널.
   * @param after "HH:MM" 이후 출발만. next 는 이것과 무관하게 항상 이 시각 이후 첫차다.
   */
  @GetMapping("/api/pois/{poiId}/departures")
  public SpotDeparturesRes departures(@PathVariable long poiId,
      @RequestParam String date,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) Long toPoiId,
      @RequestParam(required = false) String after) {

    var spot = poi(poiId);
    var target = toPoiId == null
        ? new Endpoint(null, ORIGIN_STOP, ORIGIN_NAME)
        : endpointOf(poi(toPoiId));

    boolean reversed = "origin".equalsIgnoreCase(from);
    String spotStop = (String) spot.get("timetable_stop");
    var snap = snapshots.forDate(date);

    // 스팟 쪽 정류장이 원문 격자에 없으면 시각을 낼 수 없다. 빈 목록 + 이유를 준다.
    // 배 연결(외도 유람선 ferry_links · 도선 shuttle_docks)도 없으면 「원문에 칸이 없다」가 아니라
    // **아직 시간표를 모으지 않은 곳**이다(TIMETABLE_PENDING). 배로 가는 곳(외도보타니아 · V31 부터 내도 · 지심도)은
    // 배 시간표(/ferries)가 답이라 여기선 그대로 NO_STOP_IN_TIMETABLE 이다.
    if (spotStop == null) {
      Boolean byBoat = jdbc.queryForObject("""
          SELECT EXISTS(SELECT 1 FROM ferry_links WHERE poi_id = ?)
              OR EXISTS(SELECT 1 FROM shuttle_docks WHERE poi_id = ?)""", Boolean.class, poiId, poiId);
      String reason = Boolean.TRUE.equals(byBoat) ? "NO_STOP_IN_TIMETABLE" : "TIMETABLE_PENDING";
      return res(spot, target, reversed, date, snap.dayClass().name(),
          List.of(), null, List.of(), reason, null);
    }

    String origin = reversed ? target.stop() : spotStop;
    String dest = reversed ? spotStop : target.stop();

    // 스팟 계층을 읽는다 — 격자에 칸이 없는 스팟 정류장도 시각이 나온다(클래스 주석 참고).
    var rides = SpotLayer.rides(snap, origin, dest);
    var deps = new ArrayList<Departure>();
    for (var r : rides) deps.add(departure(r));

    Departure next = null;
    if (after != null) {
      int afterMin = TimeUtil.hhmmToMin(after);
      next = rides.stream().filter(r -> r.departMin() >= afterMin).findFirst()
          .map(SpotTimetableController::departure).orElse(null);
    }

    var unknown = deps.isEmpty() ? Timetable.unknownTimeRoutes(snap, origin, dest) : List.<String>of();
    String reason = !deps.isEmpty() ? null : (unknown.isEmpty() ? "NO_SERVICE" : "UNKNOWN_TIME");

    // 출발 쪽·가는 쪽을 poi 로 — 고현터미널은 poi_kind TERMINAL 행이다(V22).
    Long terminal = terminalPoiId();
    Long fromPoi = reversed ? terminal : poiId;
    Long toPoi = reversed ? Long.valueOf(poiId) : (toPoiId != null ? toPoiId : terminal);
    var boarding = deps.isEmpty() || fromPoi == null || toPoi == null
        ? null : boarding(fromPoi, toPoi, byRoute(deps), deps);

    return res(spot, target, reversed, date, snap.dayClass().name(),
        deps, next, unknown, reason, boarding);
  }

  /**
   * 타는 곳 — 이 구간 byRoute 의 노선마다 V26 표에서 정류장을 찾는다.
   *
   * 예외 편은 **그날 시간표에 있는 편만** 싣는다. 휴일에 없는 편의 "건너편에서 타요"를 말하면 없는 버스를 안내하는 것이다.
   * 표에 없는 노선은 추측하지 않고 NOT_COLLECTED 로 말한다.
   */
  private Boarding boarding(long fromPoi, long toPoi, List<RouteSummary> routes, List<Departure> deps) {
    var place = jdbc.queryForObject("""
        SELECT short_name, poi_kind::text, lat, lng FROM pois WHERE poi_id = ?""",
        (rs, i) -> new BoardingPlace(rs.getString(1), "TERMINAL".equals(rs.getString(2)) ? "TERMINAL" : "SPOT",
            rs.getDouble(3), rs.getDouble(4)), fromPoi);

    var rows = new LinkedHashMap<String, java.util.Map<String, Object>>();
    for (var r : jdbc.queryForList("""
        SELECT route_no, status, reason_code, node_id, stop_name, lat::float8 AS lat, lng::float8 AS lng,
               distance_m, source
        FROM boarding_stops WHERE from_poi_id = ? AND to_poi_id = ?""", fromPoi, toPoi)) {
      rows.put((String) r.get("route_no"), r);
    }

    var stops = new LinkedHashMap<String, BoardingStop>();
    var unresolved = new ArrayList<Unresolved>();
    for (var route : routes) {
      var r = rows.get(route.routeNo());
      if (r == null) {
        unresolved.add(new Unresolved(route.routeNo(), "NOT_COLLECTED"));
        continue;
      }
      switch ((String) r.get("status")) {
        case "RESOLVED" -> {
          String node = (String) r.get("node_id");
          var existing = stops.get(node);
          var names = new ArrayList<>(existing == null ? List.<String>of() : existing.routes());
          names.add(route.routeNo());
          stops.put(node, new BoardingStop(node, (String) r.get("stop_name"),
              ((Number) r.get("lat")).doubleValue(), ((Number) r.get("lng")).doubleValue(),
              ((Number) r.get("distance_m")).intValue(), List.copyOf(names)));
        }
        case "UNRESOLVED" -> unresolved.add(new Unresolved(route.routeNo(), (String) r.get("reason_code")));
        default -> { /* SPLIT — 대표 핀 없이 아래 예외 편만 */ }
      }
    }

    var todays = new java.util.HashSet<String>();
    for (var d : deps) todays.add(d.routeNo() + " " + d.depart());
    var order = routes.stream().map(RouteSummary::routeNo).toList();
    var exceptions = new ArrayList<BoardingException>();
    for (var e : jdbc.queryForList("""
        SELECT e.route_no, to_char(e.depart_time, 'HH24:MI') AS depart, e.node_id, e.stop_name,
               e.lat::float8 AS lat, e.lng::float8 AS lng, e.distance_m, e.gap_m, s.node_id AS main_node
        FROM boarding_exceptions e
        JOIN boarding_stops s USING (from_poi_id, to_poi_id, route_no)
        WHERE e.from_poi_id = ? AND e.to_poi_id = ?
        ORDER BY e.depart_time""", fromPoi, toPoi)) {
      String routeNo = (String) e.get("route_no");
      if (!order.contains(routeNo) || !todays.contains(routeNo + " " + e.get("depart"))) continue;
      exceptions.add(new BoardingException(routeNo, (String) e.get("depart"), (String) e.get("node_id"),
          (String) e.get("stop_name"), ((Number) e.get("lat")).doubleValue(), ((Number) e.get("lng")).doubleValue(),
          ((Number) e.get("distance_m")).intValue(), (String) e.get("main_node"),
          e.get("gap_m") == null ? null : ((Number) e.get("gap_m")).intValue()));
    }
    exceptions.sort(java.util.Comparator.comparingInt((BoardingException x) -> order.indexOf(x.routeNo()))
        .thenComparing(BoardingException::depart));

    String source = rows.values().stream().map(r -> (String) r.get("source")).findFirst()
        .orElseGet(() -> jdbc.queryForList("SELECT source FROM boarding_stops LIMIT 1", String.class)
            .stream().findFirst().orElse("TAGO"));
    return new Boarding(place, List.copyOf(stops.values()), List.copyOf(exceptions), List.copyOf(unresolved),
        "정류소 좌표 국토교통부 TAGO · " + source.replaceFirst("^TAGO\\s*", ""));
  }

  private Long terminalPoiId() {
    return jdbc.queryForList("SELECT poi_id FROM pois WHERE poi_kind = 'TERMINAL' ORDER BY poi_id LIMIT 1", Long.class)
        .stream().findFirst().orElse(null);
  }

  // ── 조립 ──────────────────────────────────────────────────────────────────

  /** 코스 구간 줄(CourseController.legService)도 이것으로 센다 — 「시간표 ›」와 숫자가 어긋나지 않게. */
  static Departure departure(SpotLayer.Ride r) {
    return new Departure(r.routeNo(), TimeUtil.minToHHMM(r.departMin()),
        TimeUtil.minToHHMM(r.arriveMin()), r.durationMin(), r.estimated(), r.departEstimated());
  }

  private SpotDeparturesRes res(java.util.Map<String, Object> spot, Endpoint target,
      boolean reversed, String date, String dayClass, List<Departure> deps,
      Departure next, List<String> unknown, String reason, Boarding boarding) {
    String boardStop = (String) spot.get("timetable_stop");
    String alight = (String) spot.get("alight_label");
    // 뒤집힌 방향(고현 → 스팟)에서는 고현터미널에서 타므로 스팟의 하차 이름과 비교할 일이 없다.
    boolean differs = !reversed && alightDiffers(boardStop, alight);

    return new SpotDeparturesRes(
        ((Number) spot.get("poi_id")).longValue(),
        (String) spot.get("poi_name"), (String) spot.get("short_name"),
        reversed ? target.stop() : boardStop, alight, differs,
        target, date, dayClass,
        deps, deps.size(),
        deps.isEmpty() ? null : deps.get(0).depart(),
        deps.isEmpty() ? null : deps.get(deps.size() - 1).depart(),
        next, byRoute(deps), reason, unknown,
        SOURCE, baseDate(), boarding);
  }

  /**
   * 노선별로 묶어 횟수와 소요시간 폭을 낸다.
   *
   * **빠른 노선을 먼저 준다.** 화면 문구가 "고현터미널까지 약 40분 · 67-1번은 약 50분"이라
   * 대표 소요시간(가장 빠른 노선)이 앞에 오고 느린 노선이 뒤에 붙는다.
   * 같으면 자주 오는 순, 그다음 노선 번호 순 — 순서가 실행마다 흔들리지 않게 못박는다.
   */
  static List<RouteSummary> byRoute(List<Departure> deps) {
    var acc = new LinkedHashMap<String, List<Integer>>();
    for (var d : deps) {
      acc.computeIfAbsent(d.routeNo(), k -> new ArrayList<>());
      if (d.durationMin() != null) acc.get(d.routeNo()).add(d.durationMin());
    }
    var out = new ArrayList<RouteSummary>();
    for (var e : acc.entrySet()) {
      var v = e.getValue();
      long n = deps.stream().filter(d -> d.routeNo().equals(e.getKey())).count();
      if (v.isEmpty()) {
        out.add(new RouteSummary(e.getKey(), (int) n, null, null, false));
      } else {
        int hi = v.stream().mapToInt(Integer::intValue).max().getAsInt();
        int lo = v.stream().mapToInt(Integer::intValue).min().getAsInt();
        out.add(new RouteSummary(e.getKey(), (int) n, hi, lo, hi != lo));
      }
    }
    out.sort(java.util.Comparator
        .comparing(RouteSummary::durationMin,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
        .thenComparing(RouteSummary::count, java.util.Comparator.reverseOrder())
        .thenComparing(RouteSummary::routeNo));
    return out;
  }

  /** 근거 시간표의 시행일(BIS 표지 기준일). 화면 하단 "출처 … · 2026-08-18"에 쓴다. */
  private String baseDate() {
    return jdbc.queryForObject("""
        SELECT max(based_on)::text FROM timetable_versions
        WHERE source_file LIKE '%.xlsx'""", String.class);
  }

  /**
   * 내리는 정류장(alight_label, 「신촌 정류장」)과 시간표를 읽는 정류장(timetable_stop, 「지세포」)이 다른가.
   * 「학동 정류장」은 「학동」 기준 그대로라 다르지 않다. 스팟 시간표(boardStopDiffers)와 스팟 상세(`607:4` 둘째 줄)가 같이 쓴다 —
   * 규칙이 두 화면에서 어긋나면 한쪽이 거짓말을 한다.
   */
  static boolean alightDiffers(String timetableStop, String alightLabel) {
    return timetableStop != null && alightLabel != null && !alightLabel.startsWith(timetableStop);
  }

  private java.util.Map<String, Object> poi(long poiId) {
    var rows = jdbc.queryForList("""
        SELECT poi_id, poi_name, short_name, timetable_stop, alight_label
        FROM pois WHERE poi_id = ?""", poiId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 스팟");
    }
    return rows.get(0);
  }

  private static Endpoint endpointOf(java.util.Map<String, Object> p) {
    String stop = (String) p.get("timetable_stop");
    if (stop == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "이 스팟은 원문 시간표에 정류장 칸이 없어 목적지로 쓸 수 없다");
    }
    return new Endpoint(((Number) p.get("poi_id")).longValue(), stop,
        (String) p.get("short_name"));
  }
}
