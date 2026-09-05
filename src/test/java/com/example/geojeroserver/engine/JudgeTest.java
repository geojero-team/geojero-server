package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeTest {
  private static TripStop ts(String stop, StopStatus st, Integer min) {
    return new TripStop(stop, st, min);
  }
  private static Trip trip(long id, String route, TripStop... stops) {
    return new Trip(id, route, 0, null, null, List.of(stops));
  }

  static final Snapshot SNAP = new Snapshot("2026-09-09", DayClass.WEEKDAY,
      List.of(
          trip(1, "T", ts("A", StopStatus.TIME, 600), ts("B", StopStatus.TIME, 660)),
          trip(2, "T", ts("A", StopStatus.TIME, 900), ts("B", StopStatus.TIME, 960)),
          trip(3, "U", ts("B", StopStatus.TIME, 1000), ts("C", StopStatus.TEXT, null))),
      List.of());

  @Test void 이른시각_첫차선택_체인성립() {
    var r = Judge.judge(SNAP, List.of(new Leg.Bus("A", "B", false)), 500);
    assertEquals(Verdict.YES, r.feasible());
    assertEquals(600, r.legs().get(0).departMin());
    assertEquals(660, r.legs().get(0).arriveMin());
  }

  @Test void 막차지남_NO_막차시각사유() {
    var r = Judge.judge(SNAP, List.of(new Leg.Bus("A", "B", false)), 901);
    assertEquals(Verdict.NO, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("15:00")); // 900 = 15:00
  }

  @Test void 운행없는구간_NO() {
    var r = Judge.judge(SNAP, List.of(new Leg.Bus("B", "A", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("운행 없음"));
  }

  @Test void 도착미확인_UNKNOWN_성립추정금지() {
    var r = Judge.judge(SNAP, List.of(new Leg.Bus("B", "C", false)), 0);
    assertEquals(Verdict.UNKNOWN, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("[미확인]"));
  }

  @Test void FIXED구간후_연결() { // 유람선 복귀 → 막차
    var r = Judge.judge(SNAP,
        List.of(new Leg.Fixed("A", 899), new Leg.Bus("A", "B", false)), 0);
    assertEquals(Verdict.YES, r.feasible());
    assertEquals(900, r.legs().get(1).departMin());
  }

  @Test void boardOnly_탑승만확인() {
    var r = Judge.judge(SNAP, List.of(new Leg.Bus("B", "C", true)), 0);
    assertEquals(Verdict.YES, r.feasible());
  }

  @Test void lastDeparture_동작() {
    assertEquals(900, Judge.lastDeparture(SNAP, "A", "B"));
    assertNull(Judge.lastDeparture(SNAP, "C", "A"));
  }

  @Test void 정류소알림_운영상태레이어가_막는다() {
    var alerted = new Snapshot(SNAP.date(), SNAP.dayClass(), SNAP.trips(),
        List.of(new Alert("DETOUR", null, "B", "도로 유실 우회 중")));
    var r = Judge.judge(alerted, List.of(new Leg.Bus("A", "B", false)), 0);
    assertEquals(Verdict.NO, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("우회"));
  }

  @Test void 탑승정류소_시각미확인_UNKNOWN() { // A안 확장: 정차 확실·시각 미상 탑승 (남부2 도장포)
    var s = new Snapshot("2026-09-09", DayClass.WEEKDAY,
        List.of(trip(9, "N",
            ts("도장포", StopStatus.TEXT, null), ts("학동", StopStatus.TEXT, null))),
        List.of());
    var r = Judge.judge(s, List.of(new Leg.Bus("도장포", "학동", false)), 0);
    assertEquals(Verdict.UNKNOWN, r.feasible());
    assertTrue(r.legs().get(0).reason().contains("[미확인]"));
  }
}
