package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 스팟 계층 규칙 — build_spot_times.py 와 같은 답을 내는지 규칙별로 못박는다(순수, DB 없음).
 * 회차는 원문 모양을 줄여 만든 것이다. 실데이터 대조는 SpotTimetableApiTest·CourseTimetableConsistencyTest.
 */
class SpotLayerTest {
  private static int m(String hhmm) {
    return TimeUtil.hhmmToMin(hhmm);
  }

  /** "정류장=시각" 은 시각 칸, "#문장" 은 경로 문장 셀(원문 TEXT → 스냅샷에서 EMPTY). */
  private static Trip trip(String route, String... cells) {
    var stops = new ArrayList<TripStop>();
    for (String c : cells) {
      if (c.startsWith("#")) stops.add(new TripStop("고현", StopStatus.EMPTY, null, c.substring(1)));
      else {
        String[] kv = c.split("=");
        stops.add(new TripStop(kv[0], StopStatus.TIME, m(kv[1]), kv[1]));
      }
    }
    return new Trip(1, route, 0, null, null, List.copyOf(stops));
  }

  private static Snapshot snap(Trip... trips) {
    return new Snapshot("2026-09-14", DayClass.WEEKDAY, List.of(trips), List.of());
  }

  @Test void 종점_앞_가상정류장_도장포는_앞뒤_시각으로_감싼다() {
    var s = snap(trip("55", "고현=06:25", "학동=07:05", "해금강=07:15"));
    var r = SpotLayer.rides(s, "학동", "도장포");
    assertEquals(1, r.size());
    assertEquals(m("07:05"), r.get(0).departMin());
    assertEquals(m("07:15"), r.get(0).arriveMin(), "내릴 때는 뒤 정류장(해금강) 시각 — 상한");
    assertTrue(r.get(0).estimated());
  }

  @Test void 되돌아오는_편은_도장포에서_탈_때_앞_정류장_시각이다() {
    var s = snap(trip("55", "해금강=07:35", "학동=07:47", "고현=08:30"));
    var r = SpotLayer.rides(s, "도장포", "고현");
    assertEquals(1, r.size());
    assertEquals(m("07:35"), r.get(0).departMin(), "탈 때는 앞 정류장(해금강) 시각 — 하한");
    assertEquals(m("08:30"), r.get(0).arriveMin());
    assertTrue(r.get(0).estimated());
  }

  @Test void 경로문장_속_시각은_정차로_본다() {
    var s = snap(trip("32", "#연사-국도14호-송정-대계(6:00)-외포-두모실-고현행", "두모실=06:10"));
    var r = SpotLayer.rides(s, "대계", "두모실");
    assertEquals(1, r.size());
    assertEquals(m("06:00"), r.get(0).departMin());
    assertFalse(r.get(0).estimated(), "원문에 적힌 시각이라 추정이 아니다");
  }

  @Test void 서지_않는_노선의_경로문장은_세지_않는다() { // STOP_ROUTES: 대계는 32·34·2000번만
    var s = snap(trip("33", "#연사-송정-대계(6:00)-외포-두모실-고현행", "두모실=06:10"));
    assertTrue(SpotLayer.rides(s, "대계", "두모실").isEmpty());
  }

  @Test void 시각이_거꾸로인_문장은_버린다() { // 원본 오타 — 모르는 시각을 추정하지 않는다
    var s = snap(trip("32", "#대계(6:00)-외포(5:50)", "고현=07:00"));
    assertTrue(SpotLayer.rides(s, "대계", "고현").isEmpty());
  }

  @Test void 시각_칸이_거꾸로인_회차는_통째로_쓰지_않는다() {
    var s = snap(trip("50", "고현=08:00", "거제=07:40", "학동=08:30"));
    assertTrue(SpotLayer.rides(s, "고현", "학동").isEmpty());
  }

  @Test void 터미널은_고현이다() {
    var s = snap(trip("100", "터미널=07:00", "백병원=07:10", "포로수용소도착=07:20"));
    assertEquals(1, SpotLayer.rides(s, "고현", "백병원").size());
  }

  @Test void 옆_정류장이_정해진_목록에_있을_때만_끼운다() { // 대금교차로: 외포 옆이 두모실·율천·장목일 때
    var ok = snap(trip("32", "장목=06:00", "외포=06:10", "고현=07:00"));
    var r = SpotLayer.rides(ok, "장목", "대금교차로");
    assertEquals(1, r.size());
    assertEquals(m("06:10"), r.get(0).arriveMin());
    var no = snap(trip("32", "연초=06:00", "외포=06:10", "고현=07:00"));
    assertTrue(SpotLayer.rides(no, "연초", "대금교차로").isEmpty());
  }

  @Test void 같은_노선_같은_출발은_한_버스로_늦은_도착을_남긴다() { // 시트 두 곳에 실린 67-1
    var s = snap(trip("67-1", "학동=07:35", "고현=08:31"), trip("67-1", "학동=07:35", "고현=08:33"));
    var r = SpotLayer.rides(s, "학동", "고현");
    assertEquals(1, r.size());
    assertEquals(m("08:33"), r.get(0).arriveMin());
  }

  @Test void 급행2000번_대계_도착은_추정이다() { // 고현 출발 + 60분(사용자 제공 · V19)
    var s = snap(trip("2000", "고현=06:00", "대계=07:00"));
    var r = SpotLayer.rides(s, "고현", "대계");
    assertEquals(1, r.size());
    assertTrue(r.get(0).estimated());
  }

  @Test void 원문_격자는_건드리지_않는다() { // 회귀 게이트가 읽는 Timetable 은 그대로다
    var s = snap(trip("55", "고현=06:25", "학동=07:05", "해금강=07:15"));
    assertTrue(Timetable.rides(s, "학동", "도장포").isEmpty());
  }
}
