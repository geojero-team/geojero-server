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

  /**
   * recommended_courses.txt 확정본 23개 + 배 구간을 쓰는 3-11(V36) + 코스 재설계 2차 세트 새 코스 6개(V37 —
   * 3-12 · 3-13 · 4-11 · 4-12 · 5-04 · 6-01. 여섯 곳 코스는 처음이다).
   */
  @Test void 추천코스는_30개이고_코드가_스팟수_순위순이다() {
    var codes = jdbc.queryForList("""
        SELECT course_code FROM courses
        WHERE course_code IS NOT NULL ORDER BY spot_count, rank_no""", String.class);
    assertEquals(List.of(
        "3-01", "3-02", "3-03", "3-04", "3-05", "3-06", "3-07", "3-08", "3-09", "3-10", "3-11",
        "3-12", "3-13",
        "4-01", "4-02", "4-03", "4-04", "4-05", "4-06", "4-07", "4-08", "4-09", "4-10", "4-11", "4-12",
        "5-01", "5-02", "5-03", "5-04",
        "6-01"), codes);
  }

  /** 코드의 번호가 곧 rank_no 다 — 새 코스도 같은 규칙으로 「{곳수}-{번호}」를 받는다. */
  @Test void 코스코드는_곳수와_rank_no로_이뤄진다() {
    for (var c : rows("""
        SELECT course_code, spot_count, rank_no FROM courses WHERE course_code IS NOT NULL""")) {
      assertEquals(String.format("%d-%02d", ((Number) c.get("spot_count")).intValue(),
          ((Number) c.get("rank_no")).intValue()), c.get("course_code"));
    }
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
   * 그 위에 V36 의 3-11, V37 의 3-12 · 3-13 · 4-11 · 4-12 · 5-04 · 6-01 이 얹혔다.
   */
  @Test void 스팟수별_코스는_3곳13_4곳12_5곳4_6곳1이다() {
    for (int[] e : new int[][] {{3, 13}, {4, 12}, {5, 4}, {6, 1}}) {
      assertEquals(e[1], jdbc.queryForObject("""
          SELECT count(*) FROM courses WHERE course_code IS NOT NULL AND spot_count = ?""",
          Integer.class, e[0]), e[0] + "곳 코스 수");
    }
  }

  /**
   * 구간 체인이 끊기면 화면이 타임라인을 그릴 수 없다. 스팟 N곳이면 구간은 N+1개다.
   *
   * ⚠️ **배가 든 코스는 예외다.** 배는 떠난 선착장으로 **돌아오므로**(외도 왕복) 가는 구간과
   * 돌아오는 구간 둘이 되어 왕복 한 번마다 구간이 하나 더 는다 — 3-11 은 스팟 3곳에 구간 5개다.
   * 그래도 체인 자체는 이어진다(도장포 → 외도 → 도장포).
   *
   * ⚠️ **되짚기도 구간이 하나 더 는다**(V37). 두 스팟 사이에 직행이 없으면 고현터미널을 한 번 거친다 —
   * 「A → 고현터미널」(to NULL) + 「고현터미널 → B」(from NULL) 둘로 담는다. 구간마다 버스 한 대라는
   * 환승 없음 규칙이 그대로 선다. 가운데의 NULL → NULL 이음도 체인이 이어진 것이다.
   */
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
      int ferryRoundTrips = jdbc.queryForObject(
          "SELECT count(*) / 2 FROM course_legs WHERE course_id = ? AND mode = 'FERRY'",
          Integer.class, id);
      // 되짚기 = 마지막 구간이 아닌데 고현터미널로 가는 구간
      int backtracks = jdbc.queryForObject("""
          SELECT count(*) FROM course_legs l
          WHERE l.course_id = ? AND l.to_poi_id IS NULL
            AND l.leg_seq < (SELECT max(leg_seq) FROM course_legs WHERE course_id = l.course_id)""",
          Integer.class, id);
      assertEquals(spots + 1 + ferryRoundTrips + backtracks, jdbc.queryForObject(
          "SELECT count(*) FROM course_legs WHERE course_id = ?", Integer.class, id),
          code + ": 구간이 스팟 수 + 1(+ 배 왕복 수 + 되짚기 수)이 아니다 — 체인이 끊겼다");

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

  /**
   * 되짚기(고현터미널 재통과)는 코스당 한 번까지이고(사용자 결정 2026-09-16 — 코스재설계 §3),
   * 터미널에서 갈아탈 여유는 10분 이상이다. 두 구간 다 버스다 — 고현터미널은 스팟이 아니라 걸어서 옮길 곳이 없다.
   */
  @Test void 되짚기는_코스당_한번이고_터미널에서_10분_이상_갈아탄다() {
    for (var b : rows("""
        SELECT c.course_code, a.leg_seq, a.mode AS a_mode, n.mode AS n_mode, a.arrive_time, n.depart_time,
               n.from_poi_id AS n_from
        FROM course_legs a
        JOIN courses c ON c.course_id = a.course_id
        JOIN course_legs n ON n.course_id = a.course_id AND n.leg_seq = a.leg_seq + 1
        WHERE c.course_code IS NOT NULL AND a.to_poi_id IS NULL""")) {
      String where = b.get("course_code") + " 구간" + b.get("leg_seq");
      assertNull(b.get("n_from"), where + ": 고현터미널로 간 다음 구간이 고현터미널에서 떠나지 않는다");
      assertEquals("BUS", b.get("a_mode").toString(), where);
      assertEquals("BUS", b.get("n_mode").toString(), where);
      var arr = java.time.LocalTime.parse(b.get("arrive_time").toString());
      var dep = java.time.LocalTime.parse(b.get("depart_time").toString());
      assertTrue(java.time.Duration.between(arr, dep).toMinutes() >= 10,
          where + ": 고현터미널에서 갈아탈 여유가 10분이 안 된다");
    }
    assertEquals(0, jdbc.queryForObject("""
        SELECT count(*) FROM (
          SELECT l.course_id FROM course_legs l
          WHERE l.to_poi_id IS NULL
            AND l.leg_seq < (SELECT max(leg_seq) FROM course_legs WHERE course_id = l.course_id)
          GROUP BY l.course_id HAVING count(*) > 1) x""", Integer.class), "되짚기가 두 번인 코스가 있다");
    // 되짚기가 있는 코스는 V37 의 다섯 개다(① · ③ · ④ · ⑥ · ⑦). ② 는 직행으로만 잇는다.
    assertEquals(List.of("3-12", "3-13", "4-11", "4-12", "5-04"), jdbc.queryForList("""
        SELECT DISTINCT c.course_code FROM course_legs l JOIN courses c ON c.course_id = l.course_id
        WHERE l.to_poi_id IS NULL
          AND l.leg_seq < (SELECT max(leg_seq) FROM course_legs WHERE course_id = l.course_id)
        ORDER BY c.course_code""", String.class));
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
   * 원문 시간표에 시각 칸이 없는 정류장에만 붙고, 거기서는 **반드시** 붙는다. 하차는 뒤 정류장(상한)·
   * 승차는 앞 정류장(하한)이라 버스를 놓치지 않는 쪽으로만 틀린다.
   * 그런 정류장은 V20 코스에서 도장포·대금교차로·맹종죽테마파크 셋이었고, V37 이 포로수용소·식물원·
   * 옥포대첩기념공원을 더해 **여섯**이 됐다(스팟 계층 VIRTUAL_STOPS 와 같은 여섯).
   * 양끝이 다 그런 정류장인 승차는 셋이다 — 4-08 맹종죽테마파크 → 대금교차로 · 3-13 대금교차로 → 맹종죽테마파크 ·
   * 6-01 옥포대첩기념공원 → 맹종죽테마파크.
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

    // 35(recommended_courses.json) + 2(3-11 의 도장포 하차·승차 — V36)
    // + 22(V37 — 4-11 둘 · 6-01 셋 · 3-12 넷 · 3-13 다섯 · 4-12 넷 · 5-04 넷)
    assertEquals(59, est.size(), "추정이 붙은 승차 수");
    var noTimeCell = java.util.Set.of("도장포", "대금교차로", "맹종죽테마파크",
        "포로수용소", "식물원", "옥포대첩기념공원");
    assertEquals(noTimeCell, java.util.Set.copyOf(jdbc.queryForList("""
        SELECT board_stop FROM course_rides WHERE board_estimated
        UNION SELECT alight_stop FROM course_rides WHERE alight_estimated""", String.class)),
        "추정이 붙은 정류장 집합");
    for (var r : rows("""
        SELECT c.course_code, r.board_stop, r.board_estimated, r.alight_stop, r.alight_estimated
        FROM course_rides r JOIN course_legs l ON l.leg_id = r.leg_id
        JOIN courses c ON c.course_id = l.course_id WHERE c.course_code IS NOT NULL""")) {
      assertEquals(noTimeCell.contains((String) r.get("board_stop")), r.get("board_estimated"),
          "승차 추정 표시가 정류장과 맞지 않는다: " + r);
      assertEquals(noTimeCell.contains((String) r.get("alight_stop")), r.get("alight_estimated"),
          "하차 추정 표시가 정류장과 맞지 않는다: " + r);
    }
    assertEquals(List.of("3-13 대금교차로→맹종죽테마파크", "4-08 맹종죽테마파크→대금교차로",
            "6-01 옥포대첩기념공원→맹종죽테마파크"),
        est.stream().filter(e -> (Boolean) e.get("board_estimated") && (Boolean) e.get("alight_estimated"))
            .map(e -> e.get("course_code") + " " + e.get("board_stop") + "→" + e.get("alight_stop"))
            .sorted().toList(),
        "양끝이 다 추정인 승차");
  }

  /**
   * 같은 정류장 구간은 두 쌍뿐이다 — 조선해양문화관 → 씨월드(둘 다 '신촌', 9개 코스 — V37 의 6-01 포함)와
   * 도장포유람선 → 바람의언덕(둘 다 '도장포', 3-11 하나. 원문 「도보 1분거리에 바람의 언덕이 있습니다」).
   * 버스를 타지 않는다.
   *
   * ⚠️ 3-11 의 것만 **시각이 없다**. 앞이 배 구간인데 배가 몇 시에 돌아오는지는 날짜마다 달라
   * 역산할 수 없다 — 자리값을 지어 넣지 않는다(V36 이 chk_leg_same_stop 을 그렇게 풀었다).
   */
  @Test void 같은정류장_구간은_두_쌍뿐이다() {
    var same = rows("""
        SELECT c.course_code, pf.poi_name AS frm, pt.poi_name AS dst,
               l.duration_min, l.depart_time, l.arrive_time,
               (SELECT count(*) FROM course_rides r WHERE r.leg_id = l.leg_id) AS rides
        FROM course_legs l
        JOIN courses c ON c.course_id = l.course_id
        JOIN pois pf ON pf.poi_id = l.from_poi_id
        JOIN pois pt ON pt.poi_id = l.to_poi_id
        WHERE c.course_code IS NOT NULL AND l.mode = 'SAME_STOP'""");

    assertEquals(10, same.size(), "조선해양문화관 → 씨월드 9개 + 도장포유람선 → 바람의언덕 1개");
    assertEquals(9, same.stream().filter(x -> "거제조선해양문화관".equals(x.get("frm"))).count());
    for (var s : same) {
      assertTrue(("거제조선해양문화관".equals(s.get("frm")) && "거제씨월드".equals(s.get("dst")))
          || ("도장포유람선".equals(s.get("frm")) && "바람의언덕".equals(s.get("dst"))),
          "같은 정류장 쌍이 아니다: " + s);
      assertEquals(0, ((Number) s.get("duration_min")).intValue());
      assertEquals(0L, ((Number) s.get("rides")).longValue(), "버스를 타지 않는다");
      assertEquals(s.get("depart_time"), s.get("arrive_time"),
          "시각이 있으면 옮겨가는 시각 하나뿐이고, 배 뒤라면 둘 다 없다");
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

  // ── V28: 거제 9경 번호 · 대표 코스 제목·소개 (2026-09-14) ────────────────────

  /**
   * 거제시 공식 「거제 9경」 번호 — 아홉 경이 전부 스팟이다.
   * V28 은 7경(공곶이와 내도)·8경(지심도)을 배편이 없어 뺐는데, V30(2026-09-15 사용자 결정)이 두 곳을 넣었다 —
   * 7경은 공곶이 한 곳이 대표(TourAPI 2536196)이고, 시간표는 준비 중이다. 코스에는 들지 않아 코스 9경 수는 그대로다.
   */
  @Test void 거제9경_번호는_아홉경_전부_스팟이다() {
    var rows = jdbc.queryForList("""
        SELECT nine_scenic_no, poi_name FROM pois
        WHERE nine_scenic_no IS NOT NULL ORDER BY nine_scenic_no""");
    assertEquals(List.of(
        "1 해금강", "2 바람의언덕", "3 외도보타니아", "4 학동흑진주몽돌해변",
        "5 거제식물원", "6 거제도포로수용소유적공원", "7 공곶이", "8 지심도", "9 매미성"),
        rows.stream().map(r -> r.get("nine_scenic_no") + " " + r.get("poi_name")).toList());
  }

  /**
   * 9경 번호(V28 pois)와 코스의 9경 수(nine_scenic_count)가 맞는지 — 대표 코스 순위의 첫 정렬 키다.
   *
   * 팀원 파이프라인(V20)은 매미성(9경 9번, 기준문서 §6)을 9경으로 세지 않아 매미성이 든 4-03 · 4-07 · 4-08 이
   * 하나 적었다. V28 이 공식 번호로 전 코스를 다시 셌다. 팀원이 코스를 다시 적재하면서 세는 법을 안 고치면
   * 여기서 깨져서 알려준다.
   */
  @Test void 코스의_9경_스팟_수는_적재된_9경_수와_맞는다() {
    for (var c : rows("""
        SELECT c.course_code, c.nine_scenic_count,
               (SELECT count(*) FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
                WHERE cp.course_id = c.course_id AND p.nine_scenic_no IS NOT NULL) AS numbered
        FROM courses c WHERE c.course_code IS NOT NULL ORDER BY c.course_code""")) {
      int loaded = ((Number) c.get("nine_scenic_count")).intValue();
      int numbered = ((Number) c.get("numbered")).intValue();
      assertEquals(numbered, loaded,
          c.get("course_code") + ": 9경 번호가 있는 스팟 수와 nine_scenic_count 가 다르다");
    }
  }

  /**
   * 제목·소개는 옛 대표 코스(V28 의 10개 + 4-10) · 3-11(V36) · 2차 세트 새 코스 6개(V37)에 있다.
   * 옛 대표 코스의 글은 대표 목록에서 빠져도 지우지 않는다 — 코스 상세 제목으로 그대로 쓰인다.
   * 소개는 카드 한 장에 들어가야 하므로 150자 이하다. Claude 초안이라 사용자가 고칠 수 있다(V28 · V37 주석).
   */
  @Test void 제목과_소개가_있는_코스와_소개는_150자_이하다() {
    var titled = rows("""
        SELECT course_code, title, intro FROM courses
        WHERE title IS NOT NULL OR intro IS NOT NULL ORDER BY course_code""");
    assertEquals(List.of("3-01", "3-02", "3-03", "3-04", "3-05", "3-06", "3-11", "3-12", "3-13",
            "4-02", "4-03", "4-09", "4-10", "4-11", "4-12", "5-01", "5-04", "6-01"),
        titled.stream().map(r -> (String) r.get("course_code")).toList());
    for (var r : titled) {
      String title = (String) r.get("title");
      String intro = (String) r.get("intro");
      assertNotNull(title, r.get("course_code") + ": 제목이 없다");
      assertNotNull(intro, r.get("course_code") + ": 소개가 없다");
      assertTrue(intro.codePointCount(0, intro.length()) <= 150,
          r.get("course_code") + ": 소개가 150자를 넘는다(" + intro.length() + ")");
    }
  }

  // ── V37: 코스 재설계 2차 세트 — 대표 목록 순서 · 성격 축 · 거제시 공식 코스 (2026-09-17) ─────────

  /**
   * ★ 대표 목록은 사람이 고른 순서다(featured_rank) — 옛 「9경 많은 순 · 버스 짧은 순」 정렬을 버렸다.
   * 카드마다 **어느 성격 축으로 골랐는지**(badge_axis)가 붙는다. 사용자가 축을 셋으로 나눴다(코스재설계 §5-1):
   * 거제시 공식 코스(OFFICIAL) 둘 · 분류(THEME) 셋 · 거제 9경(NINE) 둘. ⑤ 는 운영 중인 3-11 을 그대로 쓴다.
   * 옛 코스 24개 중 3-11 을 뺀 23개는 대표가 아니다 — 지우지 않는다(저장 일정 · 지도 화면이 쓴다).
   */
  @Test void 대표코스는_일곱이고_순서와_성격축이_정해져_있다() {
    assertEquals(List.of("1 4-11 OFFICIAL", "2 6-01 OFFICIAL", "3 3-12 THEME", "4 3-13 THEME",
            "5 3-11 THEME", "6 4-12 NINE", "7 5-04 NINE"),
        rows("""
            SELECT featured_rank, course_code, badge_axis FROM courses
            WHERE featured_rank IS NOT NULL ORDER BY featured_rank""").stream()
            .map(r -> r.get("featured_rank") + " " + r.get("course_code") + " " + r.get("badge_axis"))
            .toList());
    assertEquals(0, jdbc.queryForObject("""
        SELECT count(*) FROM courses WHERE featured_rank IS NULL AND badge_axis IS NOT NULL""", Integer.class),
        "대표가 아닌 코스에 성격 축이 붙었다");
  }

  /**
   * ★ 대표 코스 제목은 **무엇을 보는가**만 말한다(2026-09-17 사용자 지적). 카드 사진 위 배지가 이미
   * 「거제시 당일코스의 4곳 · 원문 순서 그대로」 · 「거제 9경 ①②④⑥」을 말하는데 옛 제목이
   * 「거제시 당일코스의 네 곳, …」 · 「거제 9경 네 곳, …」로 같은 말을 한 번 더 했다.
   * 그래서 배지의 말(거제시 · 당일코스 · 2일코스 · 원문 순서 · N곳, 9경 축이면 9경)을 제목에 쓰지 않는다.
   * 길이는 카드 두 줄 안 — 두 줄에 든 것을 화면으로 본 가장 긴 제목이 ④ 25자이고, 옛 ① 31자는 세 줄로 넘쳤다.
   */
  @Test void 대표코스_제목은_배지의_말을_되풀이하지_않고_두_줄_안이다() {
    var badgeWords = java.util.regex.Pattern.compile("거제시|당일코스|2일코스|원문|순서|곳");
    for (var r : rows("""
        SELECT course_code, badge_axis, title FROM courses
        WHERE featured_rank IS NOT NULL ORDER BY featured_rank""")) {
      String title = (String) r.get("title");
      String where = r.get("course_code") + " 「" + title + "」";
      assertFalse(badgeWords.matcher(title).find(), where + ": 배지에 있는 말을 제목이 되풀이한다");
      if ("NINE".equals(r.get("badge_axis"))) {
        assertFalse(title.contains("9경"), where + ": 거제 9경 배지와 같은 말이다");
      }
      assertTrue(title.codePointCount(0, title.length()) <= 25, where + ": 카드 두 줄을 넘길 만큼 길다");
    }
  }

  /**
   * 거제시 공식 관광코스(tour.geoje.go.kr 관광코스 · 최종수정 2026-05-16) — 원문 HTML 로 직접 센 값이다.
   *   당일코스 여섯 곳: 포로수용소유적공원 · 학동흑진주몽돌해변 · 바람의언덕/신선대 · 거제해금강/외도 · 거제조선해양문화관 ·
   *     거제맹종죽테마파크 (거제대교 · 거가대교는 다리라 세지 않는다)
   *   2일코스 열여섯 곳: 1일차 일곱(청마생가/기념관 · 포로수용소유적공원 · 학동흑진주몽돌해변 · 바람의언덕/신선대 ·
   *     거제해금강/외도 · 여차-홍포해변비경 · 명사해수욕장) + 2일차 아홉(거제자연휴양림 · 공곶이 · 거제조선해양문화관 ·
   *     거제씨월드 · 능포양지암조각공원 · 조선소 견학 · 옥포대첩기념공원 · 김영삼대통령전시관/생가 · 거제맹종죽테마파크).
   *     「산방산비원」은 원문 HTML 주석 안이라 화면에 나오지 않아 세지 않는다.
   * 코스가 공식 코스를 가리키는 것과 성격 축이 OFFICIAL 인 것은 같은 말이다.
   */
  @Test void 거제시_공식_코스는_원문대로_세고_OFFICIAL_코스만_가리킨다() {
    assertEquals(List.of("당일코스 6", "2일코스 16"),
        rows("SELECT name, place_count FROM official_courses ORDER BY place_count").stream()
            .map(r -> r.get("name") + " " + r.get("place_count")).toList());
    assertEquals(List.of("4-11 당일코스 4 true", "6-01 2일코스 6 true"),
        rows("""
            SELECT c.course_code, o.name, c.official_matched, c.official_order_kept
            FROM courses c JOIN official_courses o ON o.official_code = c.official_code
            ORDER BY c.course_code""").stream()
            .map(r -> r.get("course_code") + " " + r.get("name") + " " + r.get("official_matched") + " "
                + r.get("official_order_kept"))
            .toList());
    assertEquals(0, jdbc.queryForObject("""
        SELECT count(*) FROM courses
        WHERE (badge_axis IS NOT DISTINCT FROM 'OFFICIAL') <> (official_code IS NOT NULL)""", Integer.class));
    assertEquals(List.of("https://tour.geoje.go.kr/index.geoje?menuCd=DOM_000008502008002000"),
        jdbc.queryForList("SELECT DISTINCT source_url FROM official_courses", String.class));
  }

  /**
   * ★ 새 코스 여섯의 편 — 코스재설계 편 고르기 규칙으로 서버 시간표(스팟 계층 평일 2026-09-14 · 휴일 2026-09-19)를
   * **전수로 돌려** 고른 값이다. `*` 는 앞뒤 정류장으로 감싼 추정 시각이다.
   * 버스 시각 자체는 CourseTimetableConsistencyTest 가 시간표와 대조한다. 여기서는 **고른 결과**를 못박는다 —
   * 코스를 다시 적재해 편이 바뀌면 여기가 빨개진다. 기대값을 고치기 전에 편 고르기 규칙(V37 주석)으로 다시 확인한다.
   */
  @Test void 새_코스_여섯의_편은_고른_그대로다() {
    assertEquals(List.of(
            "3-12: 55 고현 09:05→도장포 09:55* | 55 도장포 11:45*→해금강 11:55 | 55 해금강 14:48→고현 15:40"
                + " | 32-2 고현 16:02→대금교차로 16:47* | 33-2 대금교차로 18:05*→고현 18:55",
            "3-13: 50-2 고현 09:35→식물원 10:05* | 50-2 식물원 12:15*→고현 12:45 | 33 고현 13:02→대금교차로 13:47*"
                + " | 32 대금교차로 15:05*→맹종죽테마파크 15:35* | 31 맹종죽테마파크 16:57*→고현 17:18",
            "4-11: 55 고현 06:25→학동 07:05 | 55 학동 09:45→도장포 09:55* | 55 도장포 11:45*→해금강 11:55"
                + " | 55 해금강 14:48→고현 15:40 | 22 고현 16:08→지세포 16:52 | 4000 지세포 17:57→고현 18:30",
            "4-12: 100 고현 08:55→포로수용소 09:07* | 100 포로수용소 10:40*→고현 10:50 | 55 고현 11:05→학동 11:45"
                + " | 55 학동 13:45→도장포 13:55* | 55-1 도장포 15:45*→해금강 16:10 | 55 해금강 18:48→고현 19:40",
            "5-04: 32 고현 08:02→대금교차로 08:47* | 32-1 대금교차로 10:37*→능포 11:30 | 67-1 능포 12:40→학동 13:25"
                + " | 55 학동 15:00→거제 15:20 | 50 거제 16:30→고현 17:03 | 100-1 고현 17:25→포로수용소 17:37*"
                + " | 110 포로수용소 18:50*→고현 19:05",
            "6-01: 55 고현 06:25→학동 07:05 | 67-1 학동 09:00→지세포 09:27 | 같은 정류장 10:27"
                + " | 60 지세포 12:57→능포 13:18 | 32 능포 14:22→옥포대첩기념공원 14:55*"
                + " | 32 옥포대첩기념공원 16:45*→맹종죽테마파크 17:35* | 31 맹종죽테마파크 18:57*→고현 19:18"),
        rows("""
            SELECT c.course_code, string_agg(
                     CASE WHEN l.mode = 'SAME_STOP' THEN '같은 정류장 ' || to_char(l.depart_time, 'HH24:MI')
                     ELSE r.route_no || ' ' || r.board_stop || ' ' || to_char(r.board_time, 'HH24:MI')
                          || CASE WHEN r.board_estimated THEN '*' ELSE '' END
                          || '→' || r.alight_stop || ' ' || to_char(r.alight_time, 'HH24:MI')
                          || CASE WHEN r.alight_estimated THEN '*' ELSE '' END END,
                     ' | ' ORDER BY l.leg_seq) AS chain
            FROM courses c
            JOIN course_legs l ON l.course_id = c.course_id
            LEFT JOIN course_rides r ON r.leg_id = l.leg_id
            WHERE c.course_code IN ('3-12', '3-13', '4-11', '4-12', '5-04', '6-01')
            GROUP BY c.course_code ORDER BY c.course_code""").stream()
            .map(r -> r.get("course_code") + ": " + r.get("chain")).toList());
  }

  /**
   * 편 고르기 규칙이 적재분에서도 서는지 — 새 코스 여섯의 모든 이음을 본다(V37 주석 「편 고르기 규칙」).
   *   · 스팟 도착 = 앞 구간 도착, 스팟 출발 = 다음 구간 출발(체류가 구간과 이어진다)
   *   · 스팟에서 다음 버스까지 60분 이상 — 같은 정류장으로 걸어 옮기면 두 곳이라 120분
   *   · 고현터미널에서 갈아타기 10분 이상
   *   · 내리는 곳이나 다음에 타는 곳이 추정 시각이면 그 이음에 10분을 더 둔다
   * 「같은 노선인데 회차마다 소요시간이 흔들리는 구간」 규칙은 시간표가 있어야 볼 수 있어 여기서 못 본다 — V37 주석에 결과를 적었다.
   */
  @Test void 새_코스는_스팟_60분_터미널_10분_추정_이음_10분을_지킨다() {
    for (String code : List.of("3-12", "3-13", "4-11", "4-12", "5-04", "6-01")) {
      var legs = rows("""
          SELECT l.leg_seq, l.mode::text AS mode, l.from_poi_id, l.to_poi_id, l.depart_time, l.arrive_time,
                 r.board_estimated, r.alight_estimated
          FROM course_legs l JOIN courses c ON c.course_id = l.course_id
          LEFT JOIN course_rides r ON r.leg_id = l.leg_id
          WHERE c.course_code = ? ORDER BY l.leg_seq""", code);
      var spots = rows("""
          SELECT cp.poi_id, cp.arrive_time, cp.leave_time FROM course_pois cp JOIN courses c ON c.course_id = cp.course_id
          WHERE c.course_code = ? ORDER BY cp.poi_seq""", code);
      assertFalse(legs.isEmpty(), code + ": 구간이 없다");
      int spot = 0;
      for (int i = 0; i < legs.size() - 1; i++) {
        var a = legs.get(i);
        String where = code + " 구간" + a.get("leg_seq");
        var arr = java.time.LocalTime.parse(a.get("arrive_time").toString());
        if (a.get("to_poi_id") == null) {                        // 되짚기 — 고현터미널에서 갈아탄다
          var n = legs.get(i + 1);
          assertTrue(java.time.Duration.between(arr,
              java.time.LocalTime.parse(n.get("depart_time").toString())).toMinutes() >= 10, where);
          continue;
        }
        var s = spots.get(spot++);
        assertEquals(a.get("to_poi_id"), s.get("poi_id"), where + ": 구간이 닿는 곳이 다음 스팟이 아니다");
        assertEquals(a.get("arrive_time"), s.get("arrive_time"), where + ": 스팟 도착이 구간 도착과 다르다");
        if ("SAME_STOP".equals(a.get("mode"))) continue;       // 앞 스팟에서 이미 본 이음이다
        int j = i + 1;
        int walked = 0;
        while ("SAME_STOP".equals(legs.get(j).get("mode"))) {
          assertEquals(legs.get(j).get("depart_time"), s.get("leave_time"), where + ": 걸어 옮기는 시각이 체류 끝과 다르다");
          s = spots.get(spot);
          j++;
          walked++;
        }
        var b = legs.get(j);
        assertEquals(b.get("depart_time"), s.get("leave_time"), where + ": 스팟 출발이 다음 구간 출발과 다르다");
        int need = 60 * (1 + walked)
            + (Boolean.TRUE.equals(a.get("alight_estimated")) || Boolean.TRUE.equals(b.get("board_estimated")) ? 10 : 0);
        long gap = java.time.Duration.between(arr, java.time.LocalTime.parse(b.get("depart_time").toString())).toMinutes();
        assertTrue(gap >= need, where + ": 다음 버스까지 " + gap + "분 — " + need + "분 이상이어야 한다");
      }
    }
  }

  /**
   * 대표 목록 칸끼리의 약속은 CHECK 가 지킨다 — 순서 없이 성격 축만 붙거나, OFFICIAL 인데 거제시 코스를 안 가리키면 적재가 막힌다.
   * 시험 쓰기는 트랜잭션 안에서 하고 끝에 되돌린다(로컬 DB 를 더럽히지 않는다).
   */
  @Test @org.springframework.transaction.annotation.Transactional
  void 대표_칸의_약속은_CHECK_가_막는다() {
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
        jdbc.update("UPDATE courses SET badge_axis = 'THEME' WHERE course_code = '3-01'"));
  }

  @Test @org.springframework.transaction.annotation.Transactional
  void 공식코스_축은_거제시_코스를_가리켜야_한다() {
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
        jdbc.update("UPDATE courses SET badge_axis = 'NINE' WHERE course_code = '4-11'"));
  }

  /**
   * 새 코스 여섯의 사슬 모양 — 출발 · 복귀 · 마지막 스팟 도착(18:30 이하) · 복귀(20:00 이하).
   */
  @Test void 새_코스는_마지막_스팟_18시30분_복귀_20시_안에_끝난다() {
    assertEquals(List.of(
            "3-12 09:05 18:55", "3-13 09:35 17:18", "4-11 06:25 18:30",
            "4-12 08:55 19:40", "5-04 08:02 19:05", "6-01 06:25 19:18"),
        rows("""
            SELECT course_code, to_char(depart_time, 'HH24:MI') AS d, to_char(return_time, 'HH24:MI') AS r
            FROM courses WHERE course_id BETWEEN 125 AND 130 ORDER BY course_code""").stream()
            .map(r -> r.get("course_code") + " " + r.get("d") + " " + r.get("r")).toList());
    for (var r : rows("""
        SELECT c.course_code, max(cp.arrive_time) AS last_arrive, c.return_time
        FROM courses c JOIN course_pois cp ON cp.course_id = c.course_id
        WHERE c.featured_rank IS NOT NULL AND c.course_id <> 124
        GROUP BY c.course_code, c.return_time""")) {
      assertFalse(java.time.LocalTime.parse(r.get("last_arrive").toString())
          .isAfter(java.time.LocalTime.of(18, 30)), r.get("course_code") + ": 마지막 스팟 도착이 18:30 뒤다");
      assertFalse(java.time.LocalTime.parse(r.get("return_time").toString())
          .isAfter(java.time.LocalTime.of(20, 0)), r.get("course_code") + ": 복귀가 20:00 뒤다");
    }
  }

  /**
   * 스팟의 체류 시각이 앞뒤 구간과 이어지는지 — 도착 ≤ 출발이고 체류 분이 그 차이다.
   *
   * ⚠️ **배가 닿는 스팟은 시각이 없다**(3-11). 배는 날짜마다 출항이 달라 도착·출발을 역산할 수 없고,
   * 자리값을 지어 넣지 않는다. 시각이 둘 다 있는 스팟만 검사한다 — 한쪽만 있으면 그건 오류라 잡는다.
   */
  @Test void 스팟_체류시각이_앞뒤_구간과_이어진다() {
    for (var s : rows("""
        SELECT c.course_code, cp.poi_seq, cp.arrive_time, cp.leave_time, cp.stay_min
        FROM course_pois cp JOIN courses c ON c.course_id = cp.course_id
        WHERE c.course_code IS NOT NULL ORDER BY c.course_id, cp.poi_seq""")) {
      if (s.get("arrive_time") == null || s.get("leave_time") == null) {
        assertNull(s.get("stay_min"),
            s.get("course_code") + " #" + s.get("poi_seq") + ": 시각이 없는데 체류 분이 있다");
        continue;
      }
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
