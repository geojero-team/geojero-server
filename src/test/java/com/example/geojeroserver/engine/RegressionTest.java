package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 회귀 케이스 — .claude/rules/engine.md (geojero repo). 픽스처 = 스냅샷 빌더 최종 산출물.
 *
 * 2026-09-12 판정 제거로 다시 썼다. 케이스가 줄지 않았다 — **판정(YES/NO)이라는 껍데기만
 * 벗기면 안에 있던 것은 전부 데이터 사실**이고, 그게 이 게이트의 본체다.
 * 기준문서 §6 「컷 불가 바닥」의 '검증 게이트'는 판정과 함께 사라지는 것이 아니다.
 *
 * 바뀐 것은 단언의 모양뿐이다:
 *   전: judge(...) == NO          후: rides(...)가 비어 있다 / 막차가 그 시각이다
 *   전: judge(...) == YES         후: 그 구간에 회차가 있고 출발 시각이 §2와 맞다
 */
class RegressionTest {
  static Snapshot wed, sun;

  @BeforeAll
  static void load() {
    wed = FixtureLoader.load("snapshot-2026-09-09.json"); // 평일
    sun = FixtureLoader.load("snapshot-2026-09-13.json"); // 공휴일(일)
  }

  @Test void case1_55번_막차_1915() { // 존재 이유가 이 2시간 (§4 네이버 '막 17:06')
    assertEquals(TimeUtil.hhmmToMin("19:15"), Timetable.lastDeparture(wed, "고현", "해금강"));
  }

  @Test void case2_55번_6회_주말동일() {
    assertEquals(6, Timetable.countTripsOfRoute(wed, "55", 0));
    assertEquals(6, Timetable.countTripsOfRoute(sun, "55", 0));
  }

  @Test void case3_부산발_당일치기_구간이_모두_운행한다() {
    // §3 '부산발 당일치기'를 이루는 네 구간이 평일에 전부 존재하는가
    assertFalse(Timetable.rides(wed, "부산사상", "고현").isEmpty());
    assertFalse(Timetable.rides(wed, "고현", "해금강").isEmpty());
    assertFalse(Timetable.rides(wed, "해금강", "고현").isEmpty());
    assertFalse(Timetable.rides(wed, "고현", "부산사상").isEmpty());
  }

  @Test void case4_55번_첫차_0625() {
    assertEquals(TimeUtil.hhmmToMin("06:25"),
        Timetable.rides(wed, "고현", "해금강").get(0).departMin());
  }

  @Test void case5_외도풀코스_1820복귀후_1848이_있고_막차는_2005() {
    var back = Timetable.rides(wed, "해금강", "고현");
    int after1820 = TimeUtil.hhmmToMin("18:20");
    var next = back.stream().filter(r -> r.departMin() >= after1820).findFirst();
    assertTrue(next.isPresent(), "도장포 막배 18:20 복귀 뒤 탈 버스가 있어야 한다");
    assertEquals(TimeUtil.hhmmToMin("18:48"), next.get().departMin()); // §2 복귀표
    assertEquals(TimeUtil.hhmmToMin("20:05"), Timetable.lastDeparture(wed, "해금강", "고현"));
  }

  @Test void case6_여차는_정류소_격자에_없다() {
    // §2는 "54·54-1 여차 각 1왕복, 여차발 13:20이 복귀 막차"라 적지만 **우리 격자에는 없다** —
    // 원문에서 '여차'가 독립 열이 아니라 홍포 열의 주석("12:50 (여차)")이기 때문이다
    // (디자인브리프 §3 '여차 격자 부재'). 주석 정류소 승격은 파서 백로그.
    //
    // 그래서 13:20을 단언하면 **문서에는 있고 데이터에는 없는 값**을 테스트가 요구하게 된다.
    // 여기서는 '모른다'를 그대로 못박는다 — 없는 시각을 만들지 않는다(절대규칙 1).
    // 파서가 주석을 승격시키면 이 테스트가 깨지고, 그때 13:20으로 바꾸면 된다.
    assertTrue(Timetable.rides(wed, "여차", "고현").isEmpty());
    assertFalse(Timetable.hasUnknownTime(wed, "여차", "고현"));
  }

  @Test void case7a_우회알림이_홍포에_걸려있다() {
    assertTrue(wed.alerts().stream()
        .anyMatch(a -> "홍포".equals(a.stop()) && a.reason().contains("우회")));
  }

  @Test void case7b_시간표레이어로는_저구홍포가_53번_6회() {
    // 알림(운영상태)과 시간표는 다른 레이어다. 우회는 알림이 막는 것이고,
    // 시간표에는 노선이 그대로 있다. §2 "53·53-1 저구·홍포·명사 6회"의 6은
    // **53번 단독** 수치다(53-1은 1회) — 실측으로 그렇게 갈린다.
    assertEquals(6, Timetable.rides(Timetable.subsetByRoute(wed, "53"), "저구", "홍포").size());
    // 저구→홍포 전체는 54·54-1·남부3까지 더해 10회다
    assertEquals(10, Timetable.rides(wed, "저구", "홍포").size());
  }

  @Test void case8_일요일_도장포학동_운행없음() { // 남부2 휴일 운휴
    assertTrue(Timetable.rides(sun, "도장포", "학동").isEmpty());
  }

  @Test void case9_학동_13에서15회() { // "하루 4번의 섬" 아님
    long n = Timetable.countTripsServingStop(wed, "학동", 0);
    assertTrue(n >= 13 && n <= 15, "학동 접근 " + n + "회");
  }

  @Test void case10_63번은_학동_미경유() {
    assertTrue(Timetable.rides(Timetable.subsetByRoute(wed, "63"), "능포", "학동").isEmpty());
  }

  @Test void 이동시간은_도착시각을_알때만_낸다() {
    // 새 제품의 명제: "대략적 이동시간"도 원문에 시각이 있을 때만 말한다.
    // 고현→해금강은 50분(06:25→07:15)이고, 도착 시각이 없는 회차는 null을 준다.
    var r = Timetable.rides(wed, "고현", "해금강").get(0);
    assertEquals(50, r.durationMin());
    assertEquals("55", r.routeNo());
  }
}
