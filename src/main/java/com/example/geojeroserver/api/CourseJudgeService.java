package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.Judge;
import com.example.geojeroserver.engine.JudgeResult;
import com.example.geojeroserver.engine.Leg;
import com.example.geojeroserver.engine.LegResult;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.engine.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 코스 판정 로직 — 코스 API와 saved_trips(저장 시점 판정·재판정)가 공유한다. */
@Service
public class CourseJudgeService {
  private static final Map<Long, List<Leg>> COURSE_LEGS = Map.of(
      1L, List.of(new Leg.Bus("고현", "해금강", false), new Leg.Bus("해금강", "고현", false)),
      2L, List.of(new Leg.Bus("고현", "해금강", false), new Leg.Bus("해금강", "고현", false)),
      3L, List.of(new Leg.Bus("고현", "해금강", false),
          new Leg.Fixed("해금강", TimeUtil.hhmmToMin("18:20")),   // 도장포 막배 복귀 (§3 확정)
          new Leg.Bus("해금강", "고현", false)));

  private final SnapshotService snapshots;

  public CourseJudgeService(SnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  public boolean hasCourse(long courseId) {
    return COURSE_LEGS.containsKey(courseId);
  }

  public JudgeController.JudgeRes judge(long courseId, String date,
      String arrivalTime, String returnTime) {
    if (!hasCourse(courseId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 코스: " + courseId);
    }
    return judgeLegs(COURSE_LEGS.get(courseId), date, arrivalTime, returnTime);
  }

  /**
   * 사용자가 고른 스팟으로 조립된 코스를 판정한다 — 저장 일정(saved_trips.legs)이 쓴다.
   *
   * 검증 코스와 **같은 규칙**으로 판정한다: 거제 도착 앵커 → 구간들 → 귀환 검사.
   * 그래야 '내 일정'에 나란히 놓인 두 종류가 같은 뜻의 판정을 갖는다.
   */
  public JudgeController.JudgeRes judgeLegs(List<Leg> courseLegs, String date,
      String arrivalTime, String returnTime) {
    var snap = snapshots.forDate(date);
    var legs = new ArrayList<Leg>();
    legs.add(new Leg.Fixed("고현", TimeUtil.hhmmToMin(arrivalTime))); // 거제 도착 앵커
    legs.addAll(courseLegs);
    var r = Judge.judge(snap, legs, 0);

    // 귀환 검사: 마지막 도착(고현)이 예매한 귀환편 이전인가
    var resultLegs = new ArrayList<>(r.legs());
    var feasible = r.feasible();
    Integer lastArrive = null;
    for (var l : r.legs()) if (l.arriveMin() != null) lastArrive = l.arriveMin();
    int returnMin = TimeUtil.hhmmToMin(returnTime);
    if (feasible == Verdict.YES && lastArrive != null && lastArrive > returnMin) {
      resultLegs.add(new LegResult(Verdict.NO, null, null,
          "귀환 " + returnTime + " 이전 고현 복귀 불가 (도착 "
              + TimeUtil.minToHHMM(lastArrive) + ")"));
      feasible = Verdict.NO;
    } else {
      resultLegs.add(new LegResult(feasible == Verdict.YES ? Verdict.YES : feasible,
          returnMin, null, null)); // 귀환편 탑승
    }
    return JudgeController.toRes(snap, new JudgeResult(feasible, List.copyOf(resultLegs)));
  }
}
