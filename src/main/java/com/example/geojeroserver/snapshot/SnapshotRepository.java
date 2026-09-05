package com.example.geojeroserver.snapshot;

import com.example.geojeroserver.engine.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** DB → 판정 스냅샷. TS 원본: geojero repo src/snapshot/build.ts (1:1 포팅). */
@Repository
public class SnapshotRepository {
  private final JdbcTemplate jdbc;

  public SnapshotRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final class TripAcc {
    long tripId; String routeNo; int direction; String headsign;
    final List<TripStop> stops = new ArrayList<>();
  }

  public Snapshot build(LocalDate date) {
    Set<LocalDate> holidays = new HashSet<>(jdbc.query(
        "SELECT holiday_date FROM holidays",
        (rs, i) -> rs.getObject("holiday_date", LocalDate.class)));
    DayClass dayClass = TimeUtil.dayClassFor(date, holidays);
    String dayCol = dayClass == DayClass.WEEKDAY ? "t.runs_weekday" : "t.runs_holiday";

    List<Alert> alerts = jdbc.query("""
        SELECT a.kind, r.route_no, st.stop_name AS stop, a.reason FROM service_alerts a
        LEFT JOIN routes r ON r.route_id = a.route_id
        LEFT JOIN stops st ON st.stop_id = a.stop_id
        WHERE a.date_from <= ? AND (a.date_to IS NULL OR a.date_to >= ?)""",
        (rs, i) -> new Alert(rs.getString("kind"), rs.getString("route_no"),
            rs.getString("stop"), rs.getString("reason")),
        date, date);
    Set<String> suspended = alerts.stream()
        .filter(a -> "SUSPENSION".equals(a.kind()) && a.routeNo() != null)
        .map(Alert::routeNo).collect(Collectors.toSet());

    var byTrip = new LinkedHashMap<Long, TripAcc>();
    jdbc.query("""
        SELECT t.trip_id, r.route_no, t.direction, t.headsign_raw,
               ts.seq, s.stop_name, ts.status, ts.depart_min, ts.raw_text
        FROM trips t
        JOIN timetable_versions v ON v.version_id = t.version_id
          AND v.valid_from <= ? AND (v.valid_to IS NULL OR v.valid_to >= ?)
        JOIN routes r ON r.route_id = t.route_id
        JOIN trip_stops ts ON ts.trip_id = t.trip_id
        JOIN stops s ON s.stop_id = ts.stop_id
        WHERE %s
        ORDER BY t.trip_id, ts.seq""".formatted(dayCol),
        rs -> {
          String routeNo = rs.getString("route_no");
          if (suspended.contains(routeNo)) return;
          long tripId = rs.getLong("trip_id");
          var acc = byTrip.computeIfAbsent(tripId, k -> new TripAcc());
          acc.tripId = tripId;
          acc.routeNo = routeNo;
          acc.direction = rs.getInt("direction");
          acc.headsign = rs.getString("headsign_raw");
          // ⭐ A안 (2026-09-05 확정): TEXT 2종 분리 — 이 분기를 빠뜨리면 회귀 10이 무너진다.
          //    원문 프로즈(경로 설명 문장) = 미정차(EMPTY), seed의 '[미확인]'만 정차·시각미상(TEXT).
          var status = StopStatus.valueOf(rs.getString("status"));
          if (status == StopStatus.TEXT && !"[미확인]".equals(rs.getString("raw_text"))) {
            status = StopStatus.EMPTY;
          }
          acc.stops.add(new TripStop(rs.getString("stop_name"), status,
              rs.getObject("depart_min", Integer.class)));
        },
        date, date);

    List<Trip> trips = byTrip.values().stream()
        .map(a -> new Trip(a.tripId, a.routeNo, a.direction, a.headsign, List.copyOf(a.stops)))
        .toList();
    return new Snapshot(date.toString(), dayClass, trips, alerts);
  }
}
