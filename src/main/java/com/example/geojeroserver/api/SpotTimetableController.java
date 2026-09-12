package com.example.geojeroserver.api;

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
 */
@RestController
public class SpotTimetableController {
  /** 모든 코스의 출발·복귀 지점. to 를 주지 않으면 여기가 목적지다. */
  private static final String ORIGIN_STOP = "고현";
  private static final String ORIGIN_NAME = "고현터미널";
  private static final String SOURCE = "거제시 BIS 원문";

  public record Endpoint(Long poiId, String stop, String name) {}

  public record Departure(String routeNo, String depart, String arrive, Integer durationMin) {}

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
      String source, String baseDate) {}

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
    if (spotStop == null) {
      return res(spot, target, reversed, date, snap.dayClass().name(),
          List.of(), null, List.of(), "NO_STOP_IN_TIMETABLE");
    }

    String origin = reversed ? target.stop() : spotStop;
    String dest = reversed ? spotStop : target.stop();

    var rides = Timetable.rides(snap, origin, dest);
    var deps = new ArrayList<Departure>();
    for (var r : rides) {
      deps.add(new Departure(r.routeNo(),
          com.example.geojeroserver.engine.TimeUtil.minToHHMM(r.departMin()),
          r.arriveMin() == null ? null
              : com.example.geojeroserver.engine.TimeUtil.minToHHMM(r.arriveMin()),
          r.durationMin()));
    }

    Departure next = null;
    if (after != null) {
      int afterMin = com.example.geojeroserver.engine.TimeUtil.hhmmToMin(after);
      next = rides.stream().filter(r -> r.departMin() >= afterMin).findFirst()
          .map(r -> new Departure(r.routeNo(),
              com.example.geojeroserver.engine.TimeUtil.minToHHMM(r.departMin()),
              r.arriveMin() == null ? null
                  : com.example.geojeroserver.engine.TimeUtil.minToHHMM(r.arriveMin()),
              r.durationMin()))
          .orElse(null);
    }

    var unknown = deps.isEmpty() ? Timetable.unknownTimeRoutes(snap, origin, dest) : List.<String>of();
    String reason = !deps.isEmpty() ? null : (unknown.isEmpty() ? "NO_SERVICE" : "UNKNOWN_TIME");

    return res(spot, target, reversed, date, snap.dayClass().name(),
        deps, next, unknown, reason);
  }

  // ── 조립 ──────────────────────────────────────────────────────────────────

  private SpotDeparturesRes res(java.util.Map<String, Object> spot, Endpoint target,
      boolean reversed, String date, String dayClass, List<Departure> deps,
      Departure next, List<String> unknown, String reason) {
    String boardStop = (String) spot.get("timetable_stop");
    String alight = (String) spot.get("alight_label");
    // 뒤집힌 방향(고현 → 스팟)에서는 고현터미널에서 타므로 스팟의 하차 이름과 비교할 일이 없다.
    boolean differs = !reversed && boardStop != null && alight != null
        && !alight.startsWith(boardStop);

    return new SpotDeparturesRes(
        ((Number) spot.get("poi_id")).longValue(),
        (String) spot.get("poi_name"), (String) spot.get("short_name"),
        reversed ? target.stop() : boardStop, alight, differs,
        target, date, dayClass,
        deps, deps.size(),
        deps.isEmpty() ? null : deps.get(0).depart(),
        deps.isEmpty() ? null : deps.get(deps.size() - 1).depart(),
        next, byRoute(deps), reason, unknown,
        SOURCE, baseDate());
  }

  /**
   * 노선별로 묶어 횟수와 소요시간 폭을 낸다.
   *
   * **빠른 노선을 먼저 준다.** 화면 문구가 "고현터미널까지 약 40분 · 67-1번은 약 50분"이라
   * 대표 소요시간(가장 빠른 노선)이 앞에 오고 느린 노선이 뒤에 붙는다.
   * 같으면 자주 오는 순, 그다음 노선 번호 순 — 순서가 실행마다 흔들리지 않게 못박는다.
   */
  private static List<RouteSummary> byRoute(List<Departure> deps) {
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
