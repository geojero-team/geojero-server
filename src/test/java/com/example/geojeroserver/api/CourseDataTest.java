package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 게이트 3: 적재된 추천 코스(V20)가 팀원 산출물과 어긋나지 않는지 지킨다.
 *
 * 왜 필요한가 — 코스는 다시 바뀐다. 2026-09-13 에 팀원이 환승 없이 다시 계산해 전량 교체했고
 * (V20: 직행만 · 섬 코스 제외 · 23개), 배 시간표를 받으면 지심도·내도 코스가 들어온다. 그때
 * **조용히 깨지는 것**을 막는 게 이 테스트다. 여기가 빨개지면 "코스가 바뀌었다"는 신호이고, 기대값을 원문과
 * 대조해 고치면 된다 — 원문 대조 없이 숫자만 맞추면 게이트가 죽는다(기준문서 §6).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseDataTest {
  @Autowired JdbcTemplate jdbc;

  private List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  @Test void 추천코스는_23개이고_코드가_스팟수_순위순이다() { // recommended_courses.txt 확정본 그대로
    var codes = jdbc.queryForList("""
        SELECT course_code FROM courses
        WHERE course_code IS NOT NULL ORDER BY spot_count, rank_no""", String.class);
    assertEquals(List.of(
        "3-01", "3-02", "3-03", "3-04", "3-05", "3-06", "3-07", "3-08", "3-09", "3-10",
        "4-01", "4-02", "4-03", "4-04", "4-05", "4-06", "4-07", "4-08", "4-09", "4-10",
        "5-01", "5-02", "5-03"), codes);
  }

  /** V17 코스 3개는 새 목록에 같은 코스가 있어 id 를 그대로 쓴다 — 저장 일정 FK 가 가리킬 수 있다. */
  @Test void V17_코스는_같은_id로_남는다() {
    assertEquals(List.of("3-01", "5-01", "5-03"), jdbc.queryForList(
        "SELECT course_code FROM courses WHERE course_id IN (101, 102, 103) ORDER BY course_id",
        String.class));
  }

  @Test void 검증코스_3종은_그대로_남아있다() { // §3 코스 — CourseSeeder 소유, saved_trips FK 원천
    assertEquals(3, jdbc.queryForObject(
        "SELECT count(*) FROM courses WHERE course_code IS NULL", Integer.class));
  }

  /**
   * 스팟 수별 개수 — 3·4곳은 순위 상위 10개, 5곳은 가능한 3개 전부(섬 코스 제외 후).
   * 배 시간표를 받아 섬 코스를 넣으면 이 테스트가 깨져서 알려준다.
   */
  @Test void 스팟수별_코스는_3곳10_4곳10_5곳3이다() {
    for (int[] e : new int[][] {{3, 10}, {4, 10}, {5, 3}}) {
      assertEquals(e[1], jdbc.queryForObject("""
          SELECT count(*) FROM courses WHERE course_code IS NOT NULL AND spot_count = ?""",
          Integer.class, e[0]), e[0] + "곳 코스 수");
    }
  }

  /** 구간 체인이 끊기면 화면이 타임라인을 그릴 수 없다. 스팟 N곳이면 구간은 N+1개다. */
  @Test void 모든_코스의_구간체인이_끊기지_않는다() {
    for (var c : rows("""
        SELECT course_id, course_code, spot_count FROM courses
        WHERE course_code IS NOT NULL ORDER BY course_id""")) {
      long id = ((Number) c.get("course_id")).longValue();
      int spots = ((Number) c.get("spot_count")).intValue();
      String code = (String) c.get("course_code");

      assertEquals(spots, jdbc.queryForObject(
          "SELECT count(*) FROM course_pois WHERE course_id = ?", Integer.class, id),
          code + ": 스팟 수가 spot_count와 다르다");
      assertEquals(spots + 1, jdbc.queryForObject(
          "SELECT count(*) FROM course_legs WHERE course_id = ?", Integer.class, id),
          code + ": 구간이 스팟 수 + 1이 아니다 — 체인이 끊겼다");

      // 고현터미널(NULL)에서 시작해 고현터미널로 끝난다
      assertNull(jdbc.queryForObject(
          "SELECT from_poi_id FROM course_legs WHERE course_id = ? AND leg_seq = 1",
          Long.class, id), code + ": 첫 구간이 고현터미널 출발이 아니다");
      assertNull(jdbc.queryForObject("""
          SELECT to_poi_id FROM course_legs
          WHERE course_id = ? ORDER BY leg_seq DESC LIMIT 1""", Long.class, id),
          code + ": 마지막 구간이 고현터미널 복귀가 아니다");

      // 구간의 to 가 다음 구간의 from 과 이어진다
      var seq = rows("""
          SELECT leg_seq, from_poi_id, to_poi_id FROM course_legs
          WHERE course_id = ? ORDER BY leg_seq""", id);
      for (int i = 0; i < seq.size() - 1; i++) {
        assertEquals(seq.get(i).get("to_poi_id"), seq.get(i + 1).get("from_poi_id"),
            code + ": 구간 " + (i + 1) + "→" + (i + 2) + " 가 이어지지 않는다");
      }
    }
  }

  /** 환승은 제품에서 쓰지 않기로 확정했다(2026-09-12). 적재분에 하나라도 있으면 안 된다. */
  @Test void 환승이_들어간_구간은_없다() {
    assertEquals(0, jdbc.queryForObject("""
        SELECT count(*) FROM course_legs l JOIN courses c ON c.course_id = l.course_id
        WHERE c.course_code IS NOT NULL AND l.transfers > 0""", Integer.class));
    // BUS 구간은 버스 정확히 한 대다 — 두 대면 곧 환승이다
    assertEquals(0, jdbc.queryForObject("""
        SELECT count(*) FROM (
          SELECT l.leg_id FROM course_legs l
          JOIN courses c ON c.course_id = l.course_id
          LEFT JOIN course_rides r ON r.leg_id = l.leg_id
          WHERE c.course_code IS NOT NULL AND l.mode = 'BUS'
          GROUP BY l.leg_id HAVING count(r.ride_id) <> 1) x""", Integer.class));
  }

  /**
   * ★ 추정 시각이 어디에 붙어 있는지 못박는다 — 이게 이 서비스의 명제다.
   * 원문 시간표에 시각 칸이 없는 정류장(이 23개 코스에서는 도장포·대금교차로·맹종죽테마파크)에만
   * 붙고, 거기서는 **반드시** 붙는다. 하차는 뒤 정류장(상한)·승차는 앞 정류장(하한)이라
   * 버스를 놓치지 않는 쪽으로만 틀린다. 4-08 의 맹종죽테마파크 → 대금교차로 는 양끝이 다 그런
   * 정류장이라 승·하차가 함께 추정인 유일한 승차다.
   */
  @Test void 추정시각은_시각칸이_없는_정류장에만_붙어있다() {
    var est = rows("""
        SELECT c.course_code, r.route_no, r.board_stop, r.board_estimated,
               r.alight_stop, r.alight_estimated
        FROM course_rides r
        JOIN course_legs l ON l.leg_id = r.leg_id
        JOIN courses c ON c.course_id = l.course_id
        WHERE c.course_code IS NOT NULL
          AND (r.board_estimated OR r.alight_estimated)
        ORDER BY c.spot_count, c.rank_no, l.leg_seq""");

    assertEquals(35, est.size(), "추정이 붙은 승차 수(recommended_courses.json 과 같다)");
    var noTimeCell = java.util.Set.of("도장포", "대금교차로", "맹종죽테마파크");
    for (var r : rows("""
        SELECT c.course_code, r.board_stop, r.board_estimated, r.alight_stop, r.alight_estimated
        FROM course_rides r JOIN course_legs l ON l.leg_id = r.leg_id
        JOIN courses c ON c.course_id = l.course_id WHERE c.course_code IS NOT NULL""")) {
      assertEquals(noTimeCell.contains((String) r.get("board_stop")), r.get("board_estimated"),
          "승차 추정 표시가 정류장과 맞지 않는다: " + r);
      assertEquals(noTimeCell.contains((String) r.get("alight_stop")), r.get("alight_estimated"),
          "하차 추정 표시가 정류장과 맞지 않는다: " + r);
    }
    assertEquals(1, est.stream().filter(e ->
        (Boolean) e.get("board_estimated") && (Boolean) e.get("alight_estimated")).count(),
        "양끝이 다 추정인 승차는 4-08 맹종죽테마파크 → 대금교차로 하나다");
  }

  /** 같은 정류장 구간은 조선해양문화관 → 씨월드(둘 다 '신촌')뿐이다. 버스를 타지 않는다. */
  @Test void 같은정류장_구간은_조선해양문화관에서_씨월드뿐이다() {
    var same = rows("""
        SELECT c.course_code, pf.poi_name AS frm, pt.poi_name AS dst,
               l.duration_min, l.depart_time, l.arrive_time,
               (SELECT count(*) FROM course_rides r WHERE r.leg_id = l.leg_id) AS rides
        FROM course_legs l
        JOIN courses c ON c.course_id = l.course_id
        JOIN pois pf ON pf.poi_id = l.from_poi_id
        JOIN pois pt ON pt.poi_id = l.to_poi_id
        WHERE c.course_code IS NOT NULL AND l.mode = 'SAME_STOP'""");

    assertEquals(8, same.size(), "조선해양문화관 → 씨월드를 잇는 코스 8개다");
    for (var s : same) {
      assertEquals("거제조선해양문화관", s.get("frm"));
      assertEquals("거제씨월드", s.get("dst"));
      assertEquals(0, ((Number) s.get("duration_min")).intValue());
      assertEquals(0L, ((Number) s.get("rides")).longValue(), "버스를 타지 않는다");
      assertEquals(s.get("depart_time"), s.get("arrive_time"), "옮겨가는 시각 하나뿐이다");
    }
  }

  /**
   * 3-01의 구간 시각이 §2 원문과 맞는지 — 55번 고현→학동 40분 / 학동→해금강 10분.
   * 이 두 값은 기준문서 §2의 확정 데이터이므로 코스가 바뀌어도 변하지 않아야 한다.
   */
  @Test void 코스3x01의_앞두구간은_원문_55번_40분과_10분이다() {
    var legs = rows("""
        SELECT l.leg_seq, l.duration_min, r.route_no, r.board_time, r.alight_time
        FROM course_legs l
        JOIN courses c ON c.course_id = l.course_id
        LEFT JOIN course_rides r ON r.leg_id = l.leg_id
        WHERE c.course_code = '3-01' ORDER BY l.leg_seq""");

    assertEquals(4, legs.size());
    assertEquals("55", legs.get(0).get("route_no"));
    assertEquals(40, ((Number) legs.get(0).get("duration_min")).intValue(), "고현→학동 40분");
    assertEquals(10, ((Number) legs.get(1).get("duration_min")).intValue(), "학동→해금강 10분");
    assertEquals("11:05", legs.get(0).get("board_time").toString().substring(0, 5));
    assertEquals("11:45", legs.get(0).get("alight_time").toString().substring(0, 5));
  }

  /** 총 소요 분이 출발~복귀 차이와 맞는지. 화면의 "약 N시간"이 여기서 나온다. */
  @Test void 총소요분이_출발과_복귀_차이와_맞는다() {
    for (var c : rows("""
        SELECT course_code, depart_time, return_time, total_min, approx_total_min
        FROM courses WHERE course_code IS NOT NULL""")) {
      var dep = java.time.LocalTime.parse(c.get("depart_time").toString());
      var ret = java.time.LocalTime.parse(c.get("return_time").toString());
      int actual = (int) java.time.Duration.between(dep, ret).toMinutes();
      int total = ((Number) c.get("total_min")).intValue();
      assertEquals(actual, total, c.get("course_code") + ": total_min이 출발~복귀와 다르다");

      int approx = ((Number) c.get("approx_total_min")).intValue();
      assertTrue(Math.abs(approx - total) <= 30,
          c.get("course_code") + ": 표시값이 실제와 30분 이상 벌어졌다");
      assertEquals(0, approx % 30, "표시값은 30분 단위로 반올림된 값이다");
    }
  }

  /** 스팟의 체류 시각이 앞뒤 구간과 이어지는지 — 도착 ≤ 출발이고 체류 분이 그 차이다. */
  @Test void 스팟_체류시각이_앞뒤_구간과_이어진다() {
    for (var s : rows("""
        SELECT c.course_code, cp.poi_seq, cp.arrive_time, cp.leave_time, cp.stay_min
        FROM course_pois cp JOIN courses c ON c.course_id = cp.course_id
        WHERE c.course_code IS NOT NULL ORDER BY c.course_id, cp.poi_seq""")) {
      var arr = java.time.LocalTime.parse(s.get("arrive_time").toString());
      var lea = java.time.LocalTime.parse(s.get("leave_time").toString());
      int stay = ((Number) s.get("stay_min")).intValue();
      String where = s.get("course_code") + " #" + s.get("poi_seq");
      assertFalse(lea.isBefore(arr), where + ": 도착보다 출발이 이르다");
      assertEquals((int) java.time.Duration.between(arr, lea).toMinutes(), stay,
          where + ": stay_min이 도착~출발과 다르다");
      assertTrue(stay >= 60, where + ": 최소 체류 60분(팀원 규칙)을 못 채운다");
    }
  }
}
