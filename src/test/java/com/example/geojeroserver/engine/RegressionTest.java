package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 회귀 11케이스 — .claude/rules/engine.md (geojero repo). TS regression.test.ts의 1:1 이식.
 * 픽스처 = TS 스냅샷 빌더 최종 산출물. Task 4에서 DB 직결판을 추가한다(게이트 2).
 * 케이스 7은 레이어 분리(7-a/7-b, 2026-09-05 사람 판정).
 */
class RegressionTest {
  static Snapshot wed, sun;

  @BeforeAll
  static void load() {
    wed = FixtureLoader.load("snapshot-2026-09-09.json"); // 평일
    sun = FixtureLoader.load("snapshot-2026-09-13.json"); // 공휴일(일)
  }

  @Test void case1_55번_막차_1915() { // 존재 이유가 이 2시간
    assertEquals(TimeUtil.hhmmToMin("19:15"), Judge.lastDeparture(wed, "고현", "해금강"));
  }

  @Test void case2_55번_6회_주말동일() {
    assertEquals(6, Judge.countTripsOfRoute(wed, "55", 0));
    assertEquals(6, Judge.countTripsOfRoute(sun, "55", 0));
  }

  @Test void case3_부산발_당일치기_성립() {
    var r = Judge.judge(wed, List.of(
        new Leg.Bus("부산사상", "고현", false),
        new Leg.Bus("고현", "해금강", false),
        new Leg.Bus("해금강", "고현", false),
        new Leg.Bus("고현", "부산사상", true)), TimeUtil.hhmmToMin("06:50"));
    assertEquals(Verdict.YES, r.feasible());
  }

  @Test void case4_서울발_무박일출_성립_첫차0625() {
    var r = Judge.judge(wed, List.of(
        new Leg.Fixed("고현", TimeUtil.hhmmToMin("06:00")),
        new Leg.Bus("고현", "해금강", false),
        new Leg.Bus("해금강", "고현", false),
        new Leg.Bus("고현", "서울남부", true)), 0);
    assertEquals(Verdict.YES, r.feasible());
    assertEquals(TimeUtil.hhmmToMin("06:25"), r.legs().get(1).departMin());
  }

  @Test void case5_외도풀코스_성립_1848선택_막차2005존재() {
    var r = Judge.judge(wed, List.of(
        new Leg.Fixed("해금강", TimeUtil.hhmmToMin("18:20")),
        new Leg.Bus("해금강", "고현", false)), 0);
    assertEquals(Verdict.YES, r.feasible());
    assertEquals(TimeUtil.hhmmToMin("18:48"), r.legs().get(1).departMin()); // §2 복귀표
    assertEquals(TimeUtil.hhmmToMin("20:05"), Judge.lastDeparture(wed, "해금강", "고현"));
  }

  @Test void case6_여차_오후출발_불성립() {
    var r = Judge.judge(wed, List.of(
        new Leg.Bus("고현", "여차", false),
        new Leg.Bus("여차", "고현", true)), TimeUtil.hhmmToMin("13:00"));
    assertEquals(Verdict.NO, r.feasible());
  }

  @Test void case7a_우회알림활성_홍포_불성립() {
    var r = Judge.judge(wed, List.of(
        new Leg.Bus("저구", "홍포", false),
        new Leg.Bus("홍포", "저구", true)), 0);
    assertEquals(Verdict.NO, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("우회"));
  }

  @Test void case7b_알림없으면_시간표레이어로_성립() {
    var noDetour = new Snapshot(wed.date(), wed.dayClass(), wed.trips(),
        wed.alerts().stream()
            .filter(a -> !"홍포".equals(a.stop()) && !"명사".equals(a.stop())).toList());
    var r = Judge.judge(noDetour, List.of(
        new Leg.Bus("저구", "홍포", false),
        new Leg.Bus("홍포", "저구", true)), 0);
    assertEquals(Verdict.YES, r.feasible()); // 53계열 6회 — §2 주요 노선 표
  }

  @Test void case8_일요일_도장포학동_불성립() { // 남부2 휴일 운휴
    var r = Judge.judge(sun, List.of(new Leg.Bus("도장포", "학동", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
  }

  @Test void case9_학동_13에서15회() { // "하루 4번의 섬" 아님
    long n = Judge.countTripsServingStop(wed, "학동", 0);
    assertTrue(n >= 13 && n <= 15, "학동 접근 " + n + "회");
  }

  @Test void case10_63번으로_학동_불성립() { // 63은 학동 미경유
    var r = Judge.judge(Judge.subsetByRoute(wed, "63"),
        List.of(new Leg.Bus("능포", "학동", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
  }
}
