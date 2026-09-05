package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.snapshot.SnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 게이트 2: 픽스처가 아닌 실 DB(파이프라인 적재본) → SnapshotRepository → 회귀 11케이스.
 * + A안 검증 2건 (회귀 10의 NO / 남부2 평일 도장포→학동의 UNKNOWN).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegressionDbTest {
  @Autowired SnapshotRepository repo;
  Snapshot wed, sun;

  @BeforeAll
  void load() {
    wed = repo.build(LocalDate.parse("2026-09-09"));
    sun = repo.build(LocalDate.parse("2026-09-13"));
  }

  @Test void 빌더_평일_55x6_남부2_우회알림() {
    assertEquals(DayClass.WEEKDAY, wed.dayClass());
    assertEquals(6, Judge.countTripsOfRoute(wed, "55", 0));
    assertTrue(wed.trips().stream().anyMatch(t -> t.routeNo().equals("남부2")));
    assertTrue(wed.alerts().stream()
        .anyMatch(a -> "DETOUR".equals(a.kind()) && "홍포".equals(a.stop())));
  }

  @Test void 빌더_일요일_남부전노선제외() {
    assertEquals(DayClass.HOLIDAY, sun.dayClass());
    assertTrue(sun.trips().stream().noneMatch(t -> t.routeNo().startsWith("남부")));
  }

  @Test void case1_55번_막차_1915() {
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

  @Test void case5_외도풀코스_성립_1848선택_막차2005() {
    var r = Judge.judge(wed, List.of(
        new Leg.Fixed("해금강", TimeUtil.hhmmToMin("18:20")),
        new Leg.Bus("해금강", "고현", false)), 0);
    assertEquals(Verdict.YES, r.feasible());
    assertEquals(TimeUtil.hhmmToMin("18:48"), r.legs().get(1).departMin());
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
    assertEquals(Verdict.YES, r.feasible());
  }

  @Test void case8_일요일_도장포학동_불성립() {
    var r = Judge.judge(sun, List.of(new Leg.Bus("도장포", "학동", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
  }

  @Test void case9_학동_13에서15회() {
    long n = Judge.countTripsServingStop(wed, "학동", 0);
    assertTrue(n >= 13 && n <= 15, "학동 접근 " + n + "회");
  }

  @Test void case10_63번으로_학동_불성립_A안() { // 원문 프로즈=EMPTY 매핑이 지키는 케이스
    var r = Judge.judge(Judge.subsetByRoute(wed, "63"),
        List.of(new Leg.Bus("능포", "학동", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
  }

  @Test void A안_남부2_평일_도장포학동_UNKNOWN() { // '[미확인]'=TEXT 유지가 지키는 케이스
    var r = Judge.judge(wed, List.of(new Leg.Bus("도장포", "학동", false)), 0);
    assertEquals(Verdict.UNKNOWN, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("[미확인]"));
  }
}
