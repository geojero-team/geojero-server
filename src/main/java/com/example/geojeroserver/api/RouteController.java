package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.StopStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteController {
  public record RouteDto(String routeNo, String routeName, String routeType) {}
  public record RoutesRes(List<RouteDto> routes) {}
  public record StopDto(String stop, String time, String status) {}
  public record TripDto(int direction, String note, List<StopDto> stops) {}
  public record TimetableRes(String routeNo, String date, String dayClass, List<TripDto> trips) {}
  public record DepartureDto(String routeNo, String depart, String arrive) {}
  /**
   * {@code emptyReason} 은 departures 가 빈 **이유**다 — 있으면 null.
   *   UNKNOWN_TIME : 그 노선이 정차는 하지만 원문에 시각이 없다(`[미확인]`). unknownTimeRoutes 가 어느 노선인지 댄다.
   *   NO_SERVICE   : 그 노선이 거기 서지 않는다.
   * 둘을 뭉개면 §4에서 비판한 '이유 없는 빈칸'을 우리가 하는 것이다(절대규칙 3).
   */
  public record DeparturesRes(String stop, String to, String date,
                              List<DepartureDto> departures, String lastDeparture,
                              String emptyReason, List<String> unknownTimeRoutes) {}

  private final JdbcTemplate jdbc;
  private final SnapshotService snapshots;

  public RouteController(JdbcTemplate jdbc, SnapshotService snapshots) {
    this.jdbc = jdbc;
    this.snapshots = snapshots;
  }

  @GetMapping("/api/routes")
  public RoutesRes routes() {
    return new RoutesRes(jdbc.query(
        "SELECT route_no, route_name, route_type FROM routes ORDER BY route_type, route_no",
        (rs, i) -> new RouteDto(rs.getString(1), rs.getString(2), rs.getString(3))));
  }

  @GetMapping("/api/routes/{routeNo}/timetable")
  public TimetableRes timetable(@PathVariable String routeNo, @RequestParam String date) {
    var snap = snapshots.forDate(date);
    var trips = snap.trips().stream()
        .filter(t -> t.routeNo().equals(routeNo))
        .map(t -> new TripDto(t.direction(), t.noteRaw(),
            t.stops().stream().map(s2 ->
                new StopDto(s2.stop(), Fmt.hm(s2.departMin()), s2.status().name())).toList()))
        .toList();
    return new TimetableRes(routeNo, date, snap.dayClass().name(), trips);
  }

  @GetMapping("/api/stops/{stop}/departures")
  public DeparturesRes departures(@PathVariable String stop, @RequestParam String to,
      @RequestParam String date, @RequestParam(required = false) String after) {
    var snap = snapshots.forDate(date);
    int afterMin = after == null ? 0
        : com.example.geojeroserver.engine.TimeUtil.hhmmToMin(after);
    var deps = new ArrayList<DepartureDto>();
    for (var t : snap.trips()) {
      int i = -1;
      for (int k = 0; k < t.stops().size(); k++) {
        if (t.stops().get(k).stop().equals(stop)
            && t.stops().get(k).status() == StopStatus.TIME) { i = k; break; }
      }
      if (i < 0) continue;
      int j = -1;
      for (int k = i + 1; k < t.stops().size(); k++) {
        if (t.stops().get(k).stop().equals(to)) { j = k; break; }
      }
      if (j < 0) continue;
      var st = t.stops().get(j).status();
      if (st == StopStatus.SKIP || st == StopStatus.EMPTY) continue;
      int d = t.stops().get(i).departMin();
      if (d < afterMin) continue;
      deps.add(new DepartureDto(t.routeNo(), Fmt.hm(d), Fmt.hm(t.stops().get(j).departMin())));
    }
    deps.sort(Comparator.comparing(DepartureDto::depart));

    // 빈 결과의 이유를 함께 준다. 엔진에 판별기가 있는데 API가 안 내보내면
    // 화면은 "운행 없음"으로 뭉갤 수밖에 없다 — 그게 우리가 지적하는 실패 방식이다.
    var unknown = deps.isEmpty()
        ? com.example.geojeroserver.engine.Timetable.unknownTimeRoutes(snap, stop, to)
        : List.<String>of();
    String reason = !deps.isEmpty() ? null : (unknown.isEmpty() ? "NO_SERVICE" : "UNKNOWN_TIME");

    return new DeparturesRes(stop, to, date, deps,
        deps.isEmpty() ? null : deps.get(deps.size() - 1).depart(), reason, unknown);
  }
}
