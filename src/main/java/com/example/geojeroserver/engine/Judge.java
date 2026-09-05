package com.example.geojeroserver.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 코스 판정 엔진 — 순수 클래스. DB·HTTP·파일·시계 접근 금지 (engine.md).
 * 3분법: 성립(YES) / 불성립(NO, 이유 필수) / 미확인(UNKNOWN — 성립으로 추정하지 않는다).
 * TS 원본: geojero repo src/engine/judge.ts (회귀 22케이스가 포팅 정확성을 판정).
 */
public final class Judge {
  private Judge() {}

  private record Candidate(Trip trip, int departMin, Integer arriveMin) {}

  private static int indexOf(Trip t, String stop, int from) {
    for (int k = from; k < t.stops().size(); k++) {
      if (t.stops().get(k).stop().equals(stop)) return k;
    }
    return -1;
  }

  private static List<Candidate> candidates(Snapshot s, String from, String to) {
    var out = new ArrayList<Candidate>();
    for (Trip t : s.trips()) {
      int i = indexOf(t, from, 0);
      if (i < 0 || t.stops().get(i).status() != StopStatus.TIME) continue;
      if (to != null) {
        int j = indexOf(t, to, i + 1);
        if (j < 0) continue;
        var st = t.stops().get(j).status();
        if (st == StopStatus.SKIP || st == StopStatus.EMPTY) continue;
        out.add(new Candidate(t, t.stops().get(i).departMin(),
            st == StopStatus.TIME ? t.stops().get(j).departMin() : null));
      } else {
        out.add(new Candidate(t, t.stops().get(i).departMin(), null));
      }
    }
    out.sort(Comparator.comparingInt(Candidate::departMin));
    return out;
  }

  public static JudgeResult judge(Snapshot s, List<Leg> legs, int startMin) {
    var results = new ArrayList<LegResult>();
    int t = startMin;
    Verdict overall = Verdict.YES;
    for (Leg leg : legs) {
      if (overall == Verdict.NO) {
        results.add(new LegResult(Verdict.NO, null, null, "이전 구간 불성립"));
        continue;
      }
      if (leg instanceof Leg.Fixed f) {
        t = f.endMin();
        results.add(new LegResult(Verdict.YES, null, f.endMin(), null));
        continue;
      }
      var bus = (Leg.Bus) leg;
      // 운영상태 레이어: 정류소 스코프 알림은 시간표와 무관하게 막는다 (engine.md)
      var blocked = s.alerts().stream()
          .filter(a -> a.stop() != null
              && (a.stop().equals(bus.from()) || (!bus.boardOnly() && a.stop().equals(bus.to()))))
          .findFirst();
      if (blocked.isPresent()) {
        results.add(new LegResult(Verdict.NO, null, null, "운영상태: " + blocked.get().reason()));
        overall = Verdict.NO;
        continue;
      }
      var cands = candidates(s, bus.from(), bus.boardOnly() ? null : bus.to());
      if (cands.isEmpty()) {
        results.add(new LegResult(Verdict.NO, null, null,
            bus.from() + "→" + bus.to() + " 운행 없음"));
        overall = Verdict.NO;
        continue;
      }
      final int now = t;
      var pick = cands.stream().filter(c -> c.departMin() >= now).findFirst();
      if (pick.isEmpty()) {
        var last = cands.get(cands.size() - 1);
        results.add(new LegResult(Verdict.NO, null, null,
            "막차 " + TimeUtil.minToHHMM(last.departMin())
                + " 지남 (기준 " + TimeUtil.minToHHMM(now) + ")"));
        overall = Verdict.NO;
        continue;
      }
      var p = pick.get();
      if (!bus.boardOnly() && p.arriveMin() == null) {
        results.add(new LegResult(Verdict.UNKNOWN, p.departMin(), null,
            bus.to() + " 도착 시각 [미확인] — 성립으로 추정하지 않음"));
        if (overall == Verdict.YES) overall = Verdict.UNKNOWN;
        continue;
      }
      results.add(new LegResult(Verdict.YES, p.departMin(), p.arriveMin(), null));
      if (p.arriveMin() != null) t = p.arriveMin();
    }
    return new JudgeResult(overall, List.copyOf(results));
  }

  public static Integer lastDeparture(Snapshot s, String from, String to) {
    var c = candidates(s, from, to);
    return c.isEmpty() ? null : c.get(c.size() - 1).departMin();
  }

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

  public static Snapshot subsetByRoute(Snapshot s, String routeNo) {
    return new Snapshot(s.date(), s.dayClass(),
        s.trips().stream().filter(t -> t.routeNo().equals(routeNo)).toList(), s.alerts());
  }
}
