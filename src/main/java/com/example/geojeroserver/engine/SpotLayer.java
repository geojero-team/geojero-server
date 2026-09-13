package com.example.geojeroserver.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 스팟 계층 — 원문 격자(Snapshot) 위에서 "스팟에서 타고 내리는 시각"을 낸다. 순수 클래스.
 *
 * 왜 따로 두는가
 *   원문 격자는 BIS 표 그대로다(Timetable 이 읽는다). 그런데 스팟 정류장 가운데 여럿은
 *   격자에 칸이 없다 — 55번 표에 도장포가 없고, 대계는 경로 문장 안에만 시각이 있다.
 *   팀원 파이프라인(geojero-client/timetable/build_spot_times.py → spot_times.json,
 *   recommended_courses.json)은 사람이 확인한 규칙으로 그 빈칸을 메웠고, 추천 코스(V17·V20)는
 *   그 결과로 계산됐다. 스팟 시간표가 격자만 읽으면 코스는 "55번 · 12분"이라는데 시간표는
 *   "운행 없음"이라고 말하게 된다(2026-09-13 실제로 그랬다). 그래서 **스팟 시간표는 이 계층을,
 *   원문 조회(/api/stops)와 회귀 게이트는 격자를 그대로** 읽는다.
 *
 * 규칙 — build_spot_times.py 와 1:1 이다(값을 바꾸면 양쪽을 같이 바꾼다).
 *   1. 경로 문장 속 시각: "…-대계(06:00)-외포-…" 의 "지명(시각)" 을 그 회차의 정차로 본다.
 *      문장 안 시각이 거꾸로면(원본 오타) 그 문장은 버린다.
 *   2. 시각 칸이 거꾸로인 회차는(원본 오타) 통째로 쓰지 않는다 — 모르는 시각을 추정하지 않는다.
 *   3. 시각 없는 정류장(VIRTUAL_STOPS)을 시각 있는 옆 정류장 사이에 끼운다. 탈 때는 앞 정류장
 *      시각(하한), 내릴 때는 뒤 정류장 시각(상한) — 버스를 놓치지 않는 쪽으로만 틀린다.
 *   4. 그 정류장에 실제로 서는 노선만 센다(STOP_ROUTES).
 *   5. 한 회차 안에서 같은 구간을 두 번 탈 수 있으면(왕복·순환) 가장 짧은 것만 쓴다.
 *   6. 같은 노선·같은 출발 시각은 같은 버스다(시트 두 곳에 실린 67·67-1) — 늦은 도착을 남긴다.
 *   7. 급행 2000번 대계 도착은 고현 출발 + 60분(사용자 제공값, V19). 원문에 대계 시각이 없어
 *      추정으로 표시한다.
 * 추정(3·7)이 섞인 승차는 {@link Ride#estimated()} 가 참이다 — 화면이 확정 시각과 갈라 말해야 한다.
 */
public final class SpotLayer {
  private SpotLayer() {}

  /** 고현터미널 표기 통일 — 원문이 칸 머리와 경로 문장에서 이렇게도 적는다. */
  static final Map<String, String> ALIAS = Map.of(
      "터미널", "고현", "터미널순환", "고현", "터미널홈", "고현");

  /** 시각 칸이 없는 정류장. side 가 null 이면 앵커가 종점이라 이웃이 하나뿐일 때만 끼운다. */
  record Virtual(String name, String anchor, Set<String> side) {}

  static final List<Virtual> VIRTUAL_STOPS = List.of(
      new Virtual("도장포", "해금강", null),                               // 55: 학동→도장포→해금강(종점)
      new Virtual("식물원", "외간교회", null),                             // 50-2: …거제면사무소-식물원-외간교회(종점)
      new Virtual("대금교차로", "외포", Set.of("두모실", "율천", "장목")),     // 장목·두모실 쪽에서 외포 바로 앞
      new Virtual("포로수용소", "백병원", Set.of("시청")),                   // 100·110: 백병원-포로수용소-…-시청
      new Virtual("옥포대첩기념공원", "덕포", Set.of("중앙시장")),            // 덕포와 중앙시장 사이
      new Virtual("맹종죽테마파크", "하청", Set.of("실전", "석포", "장목")));  // 거제북로 위 하청 다음

  /** 원문에 이름이 적혀 있어도 그 정류장에 서는 노선은 이것뿐이다(사용자 확인 2026-09-12). */
  static final Map<String, Set<String>> STOP_ROUTES = Map.of(
      "대계", Set.of("32", "34", "2000"),
      "맹종죽테마파크", Set.of("30", "30-1", "30-2", "31", "32", "33", "35", "37", "37-2"));

  private static final Pattern PLACE_TIME = Pattern.compile("([가-힣@]+)\\((\\d{1,2}):(\\d{2})\\)");

  /** 한 번 타는 것. estimated 면 승차 또는 하차 시각이 추정이다(규칙 3·7). */
  public record Ride(String routeNo, int departMin, int arriveMin, boolean estimated) {
    public int durationMin() {
      return arriveMin - departMin;
    }
  }

  /** 끼운 뒤의 한 칸. 실제 정류장은 board == alight 다. */
  record Point(String stop, int board, int alight, boolean estimated) {}

  /** build_spot_times.canon — 공백 제거, 끝의 '발'·'통과' 제거, 터미널 → 고현. */
  static String canon(String name) {
    String n = name.replace(" ", "").replaceAll("(발|통과)$", "");
    return ALIAS.getOrDefault(n, n);
  }

  /** 회차 하나를 시각 순 정차 목록으로 — 규칙 1~4. */
  static List<Point> sequence(Trip t) {
    var cols = new ArrayList<Point>();
    for (var s : t.stops()) {
      if (s.status() == StopStatus.TIME && s.departMin() != null) {
        cols.add(new Point(canon(s.stop()), s.departMin(), s.departMin(), false));
      }
    }
    boolean drop = !ordered(cols);                         // 규칙 2
    var picked = new LinkedHashSet<Point>();
    if (!drop) picked.addAll(cols);
    for (var s : t.stops()) {                              // 규칙 1
      if (s.raw() == null || drop) continue;
      var found = new ArrayList<Point>();
      var m = PLACE_TIME.matcher(s.raw().replace("터미널 홈", "터미널홈"));
      while (m.find()) {
        int min = Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
        found.add(new Point(canon(m.group(1)), min, min, false));
      }
      if (ordered(found)) picked.addAll(found);
    }
    var byTime = new ArrayList<>(picked);
    byTime.sort(Comparator.comparingInt(Point::board).thenComparing(Point::stop));
    var seq = withVirtuals(byTime);                        // 규칙 3
    seq.removeIf(p -> {                                    // 규칙 4
      var only = STOP_ROUTES.get(p.stop());
      return only != null && !only.contains(t.routeNo());
    });
    if ("2000".equals(t.routeNo())) {                      // 규칙 7
      seq.replaceAll(p -> "대계".equals(p.stop())
          ? new Point(p.stop(), p.board(), p.alight(), true) : p);
    }
    return seq;
  }

  private static boolean ordered(List<Point> ps) {
    for (int i = 1; i < ps.size(); i++) {
      if (ps.get(i).board() < ps.get(i - 1).board()) return false;
    }
    return true;
  }

  /** build_spot_times.with_virtuals — 앵커 옆에 끼우고 앞뒤 시각으로 범위를 잡는다. */
  static List<Point> withVirtuals(List<Point> stops) {
    var seq = new ArrayList<>(stops);
    record Insert(int pos, Point p) {}
    var inserts = new ArrayList<Insert>();
    for (var v : VIRTUAL_STOPS) {
      for (int k = 0; k < seq.size(); k++) {
        if (!seq.get(k).stop().equals(v.anchor())) continue;
        var nbs = new ArrayList<Integer>();
        if (v.side() == null) {                 // 종점: 이웃이 하나뿐일 때만
          if (k == seq.size() - 1) nbs.add(k - 1);
          else if (k == 0) nbs.add(k + 1);
        } else {
          for (int nb : new int[] {k - 1, k + 1}) {
            if (nb >= 0 && nb < seq.size() && v.side().contains(seq.get(nb).stop())) nbs.add(nb);
          }
        }
        for (int nb : nbs) {
          if (nb < 0 || nb >= seq.size()) continue;
          int lo = Math.min(k, nb), hi = Math.max(k, nb);
          inserts.add(new Insert(hi,
              new Point(v.name(), seq.get(lo).board(), seq.get(hi).alight(), true)));
        }
      }
    }
    inserts.sort(Comparator.comparingInt(Insert::pos).reversed()); // 뒤에서부터 끼워야 앞 위치가 안 밀린다
    for (var in : inserts) seq.add(in.pos(), in.p());
    return seq;
  }

  /** from 에서 타서 to 에서 내리는 승차를 출발 시각 순으로 — 규칙 5·6. */
  public static List<Ride> rides(Snapshot s, String from, String to) {
    var byDepart = new LinkedHashMap<String, Ride>();
    for (Trip t : s.trips()) {
      var seq = sequence(t);
      Ride best = null;
      for (int i = 0; i < seq.size(); i++) {
        var a = seq.get(i);
        if (!a.stop().equals(from)) continue;
        for (int j = i + 1; j < seq.size(); j++) {
          var b = seq.get(j);
          if (!b.stop().equals(to)) continue;
          int ride = b.alight() - a.board();
          if (ride > 0 && (best == null || ride < best.durationMin())) {
            best = new Ride(t.routeNo(), a.board(), b.alight(), a.estimated() || b.estimated());
          }
        }
      }
      if (best == null) continue;
      String key = best.routeNo() + "@" + best.departMin();
      var prev = byDepart.get(key);
      if (prev == null || best.arriveMin() > prev.arriveMin()) {
        byDepart.put(key, new Ride(best.routeNo(), best.departMin(), best.arriveMin(),
            best.estimated() || (prev != null && prev.estimated())));
      } else if (best.estimated() && !prev.estimated()) {
        byDepart.put(key, new Ride(prev.routeNo(), prev.departMin(), prev.arriveMin(), true));
      }
    }
    var out = new ArrayList<>(byDepart.values());
    out.sort(Comparator.comparingInt(Ride::departMin).thenComparing(Ride::routeNo));
    return out;
  }
}
