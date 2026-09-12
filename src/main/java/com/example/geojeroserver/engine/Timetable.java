package com.example.geojeroserver.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 시간표 조회 — 순수 클래스. DB·HTTP·파일·시계 접근 금지 (engine.md).
 *
 * Judge(코스 성립 판정)를 걷어내며 남긴 것들이다(2026-09-12). 판정은 제품에서 빠졌지만
 * **판정이 쓰던 조회는 새 제품의 재료 그 자체**다 —
 *   rides()                → 구간 이동시간 + 이동 방법(노선 번호)
 *   countTripsOfRoute()    → 배차 횟수 ("1일 6회")
 *   countTripsServingStop() → 정류소 기준 배차 횟수 (§2 "학동 13~15회" 산식)
 *   lastDeparture()        → 막차
 *
 * 막차는 판정용이 아니어도 버리지 않는다. §4의 증거가 **"네이버 막 17:06 vs 실제 19:15"**이고,
 * 그 2시간이 이 프로젝트의 논거다. 회귀 case1이 그 값을 지킨다.
 *
 * 상태 의미는 Judge에서 그대로 가져왔다:
 *   TIME  — 시각이 있다
 *   TEXT  — 정차는 확실하나 시각 미상('[미확인]'). 있다고도 없다고도 하지 않는다
 *   EMPTY — 이 회차는 그 칸이 비어 있다 (미정차)
 *   SKIP  — 원문이 '미경유'라고 적었다
 */
public final class Timetable {
  private Timetable() {}

  /**
   * 한 회차로 from→to를 타는 것. arriveMin이 null이면 **도착 시각이 [미확인]**이다 —
   * 정차는 하지만 원문에 시각이 없다. 0이나 추정값으로 채우지 않는다(절대규칙 1).
   */
  public record Ride(String routeNo, int departMin, Integer arriveMin) {
    /** 소요 분. 도착 시각을 모르면 null — "대략"이라도 만들어 내지 않는다. */
    public Integer durationMin() {
      return arriveMin == null ? null : arriveMin - departMin;
    }
  }

  private static int indexOf(Trip t, String stop, int from) {
    for (int k = from; k < t.stops().size(); k++) {
      if (t.stops().get(k).stop().equals(stop)) return k;
    }
    return -1;
  }

  /**
   * from에서 타고 to에서 내리는 회차를 출발 시각 순으로 모은다.
   * to가 null이면 '타기만' 하는 것으로 보고 from의 출발 시각만 모은다.
   *
   * from은 **시각이 있어야(TIME)** 한다 — 언제 타는지 모르면 이동시간을 말할 수 없다.
   * to는 SKIP·EMPTY면 버린다(그 회차는 거기 서지 않는다). TEXT면 담되 arriveMin은 null이다.
   */
  public static List<Ride> rides(Snapshot s, String from, String to) {
    var out = new ArrayList<Ride>();
    for (Trip t : s.trips()) {
      int i = indexOf(t, from, 0);
      if (i < 0 || t.stops().get(i).status() != StopStatus.TIME) continue;
      if (to == null) {
        out.add(new Ride(t.routeNo(), t.stops().get(i).departMin(), null));
        continue;
      }
      int j = indexOf(t, to, i + 1);
      if (j < 0) continue;
      var st = t.stops().get(j).status();
      if (st == StopStatus.SKIP || st == StopStatus.EMPTY) continue;
      out.add(new Ride(t.routeNo(), t.stops().get(i).departMin(),
          st == StopStatus.TIME ? t.stops().get(j).departMin() : null));
    }
    out.sort(Comparator.comparingInt(Ride::departMin));
    return out;
  }

  /**
   * 정차는 확실하나 시각이 미상(TEXT='[미확인]')인 회차가 있는가.
   *
   * **이 메서드가 이 서비스의 명제를 지킨다.** rides()가 비었다는 것은 두 가지 뜻일 수 있다 —
   * 정말 버스가 없거나, 버스는 서는데 원문에 시각이 없거나. 둘을 뭉개면 §4에서 비판한
   * "8.17 재난 운휴를 이유 없는 빈칸으로 표시한 지도앱"과 같은 일을 우리가 하게 된다
   * (CLAUDE.md 절대규칙 3).
   *
   * 그래서 화면은 이렇게 갈라 말해야 한다:
   *   rides 있음                        → "55번 · 50분"
   *   rides 없음 + hasUnknownTime true  → "남부2 정차 · 시각 [미확인]"
   *   rides 없음 + hasUnknownTime false → "운행 없음"
   *
   * 판정을 걷어내도 이 셋은 남는다. 판정은 이 사실에 YES/NO를 붙이던 층이었을 뿐이다.
   */
  public static boolean hasUnknownTime(Snapshot s, String from, String to) {
    for (Trip t : s.trips()) {
      int i = indexOf(t, from, 0);
      if (i < 0 || t.stops().get(i).status() != StopStatus.TEXT) continue;
      if (to == null) return true;
      int j = indexOf(t, to, i + 1);
      if (j >= 0) {
        var st = t.stops().get(j).status();
        if (st != StopStatus.SKIP && st != StopStatus.EMPTY) return true;
      }
    }
    return false;
  }

  /** from→to 마지막 출발 시각. 없으면 null. */
  public static Integer lastDeparture(Snapshot s, String from, String to) {
    var r = rides(s, from, to);
    return r.isEmpty() ? null : r.get(r.size() - 1).departMin();
  }

  /** 노선의 회차 수. direction이 null이면 양방향 합. */
  public static long countTripsOfRoute(Snapshot s, String routeNo, Integer direction) {
    return s.trips().stream()
        .filter(t -> t.routeNo().equals(routeNo)
            && (direction == null || t.direction() == direction))
        .count();
  }

  /** TIME 앵커가 있거나 프로즈 회차(headsign)에 정류소명이 든 회차 수 — §2 "학동 13~15회" 산식. */
  public static long countTripsServingStop(Snapshot s, String stop, int direction) {
    return s.trips().stream()
        .filter(t -> t.direction() == direction
            && (t.stops().stream().anyMatch(x -> x.stop().equals(stop) && x.status() == StopStatus.TIME)
                || (t.headsign() != null && t.headsign().contains(stop))))
        .count();
  }

  /** 한 노선만 남긴 스냅샷. 회차마다 경로가 다른 노선을 따로 볼 때 쓴다. */
  public static Snapshot subsetByRoute(Snapshot s, String routeNo) {
    return new Snapshot(s.date(), s.dayClass(),
        s.trips().stream().filter(t -> t.routeNo().equals(routeNo)).toList(), s.alerts());
  }
}
