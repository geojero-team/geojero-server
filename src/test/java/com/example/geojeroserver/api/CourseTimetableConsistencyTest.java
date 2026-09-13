package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.engine.Snapshot;
import com.example.geojeroserver.engine.SpotLayer;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.snapshot.SnapshotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 게이트 4: 추천 코스와 스팟 시간표가 **같은 말**을 하는가.
 *
 * 2026-09-13 이전에는 코스가 "해금강 → 바람의언덕 55번 · 12분"이라는데 스팟 시간표는 같은 구간을
 * "운행 없음"이라고 했다 — 코스는 팀원 파이프라인에서, 시간표는 원문 격자에서 따로 왔기 때문이다.
 * 이제 스팟 시간표는 스팟 계층(SpotLayer)을 읽고, 이 테스트가 둘을 묶는다: 적재된 추천 코스의
 * 모든 버스(course_rides)가 같은 날(평일) 스팟 계층에 **같은 노선·같은 승차·같은 하차 시각**으로
 * 있어야 하고, 추정 표시도 같아야 한다. 코스를 다시 적재하거나 시간표를 교체하면 여기서 드러난다.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseTimetableConsistencyTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired SnapshotRepository repo;
  Snapshot mon;

  @BeforeAll
  void load() {
    mon = repo.build(LocalDate.parse("2026-09-14")); // 월요일 — 코스는 평일 기준으로 계산됐다
  }

  @Test void 코스의_모든_버스가_스팟시간표에_같은_시각으로_있다() {
    var rides = jdbc.queryForList("""
        SELECT c.course_code, l.leg_seq, r.route_no, r.board_stop, r.board_time, r.board_estimated,
               r.alight_stop, r.alight_time, r.alight_estimated
        FROM course_rides r
        JOIN course_legs l ON l.leg_id = r.leg_id
        JOIN courses c ON c.course_id = l.course_id
        WHERE c.course_code IS NOT NULL
        ORDER BY c.spot_count, c.rank_no, l.leg_seq""");
    assertFalse(rides.isEmpty(), "적재된 추천 코스 버스가 없다");

    var problems = new ArrayList<String>();
    for (var r : rides) {
      String route = (String) r.get("route_no");
      int board = TimeUtil.hhmmToMin(r.get("board_time").toString().substring(0, 5));
      int alight = TimeUtil.hhmmToMin(r.get("alight_time").toString().substring(0, 5));
      boolean est = (Boolean) r.get("board_estimated") || (Boolean) r.get("alight_estimated");
      String where = r.get("course_code") + " 구간" + r.get("leg_seq") + " " + route + "번 "
          + r.get("board_stop") + "→" + r.get("alight_stop");

      var hit = SpotLayer.rides(mon, (String) r.get("board_stop"), (String) r.get("alight_stop"))
          .stream()
          .filter(x -> x.routeNo().equals(route) && x.departMin() == board && x.arriveMin() == alight)
          .findFirst();
      if (hit.isEmpty()) problems.add(where + ": 스팟 시간표에 같은 시각의 버스가 없다");
      else if (hit.get().estimated() != est) problems.add(where + ": 추정 표시가 다르다");
    }
    assertTrue(problems.isEmpty(), String.join("\n", problems));
  }
}
