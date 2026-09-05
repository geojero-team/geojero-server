package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.Judge;
import com.example.geojeroserver.engine.Leg;
import com.example.geojeroserver.engine.LegResult;
import com.example.geojeroserver.engine.JudgeResult;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.engine.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {
  public record CourseDto(long courseId, String name, String theme, String summary) {}
  public record CoursesRes(List<CourseDto> courses) {}
  public record CourseJudgeReq(String date, String arrivalTime, String returnTime) {}

  /** §3 검증 코스 3종 — 확정 데이터 기반 상수 (DB화는 후속). */
  static final List<CourseDto> COURSES = List.of(
      new CourseDto(1, "부산발 당일치기", "ISLAND",
          "사상 07:00 → 바람의언덕·유람선 → 막차 복귀 → 부산행 21:10"),
      new CourseDto(2, "서울발 무박 일출", "NATURE",
          "서울남부 23:30 → 첫차 06:25 → 일출 → 상행 22:00"),
      new CourseDto(3, "외도 풀코스", "ISLAND",
          "도장포 막배 15:30 → 18:20 복귀 → 버스 연결"));

  private static final Map<Long, List<Leg>> COURSE_LEGS = Map.of(
      1L, List.of(new Leg.Bus("고현", "해금강", false), new Leg.Bus("해금강", "고현", false)),
      2L, List.of(new Leg.Bus("고현", "해금강", false), new Leg.Bus("해금강", "고현", false)),
      3L, List.of(new Leg.Bus("고현", "해금강", false),
          new Leg.Fixed("해금강", TimeUtil.hhmmToMin("18:20")),   // 도장포 막배 복귀 (§3 확정)
          new Leg.Bus("해금강", "고현", false)));

  private final SnapshotService snapshots;

  public CourseController(SnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @GetMapping("/api/courses")
  public CoursesRes courses() {
    return new CoursesRes(COURSES);
  }

  @PostMapping("/api/courses/{courseId}/judge")
  public JudgeController.JudgeRes judgeCourse(@PathVariable long courseId,
      @RequestBody CourseJudgeReq req) {
    var snap = snapshots.forDate(req.date());
    var legs = new ArrayList<Leg>();
    legs.add(new Leg.Fixed("고현", TimeUtil.hhmmToMin(req.arrivalTime()))); // 거제 도착 앵커
    legs.addAll(COURSE_LEGS.get(courseId));
    var r = Judge.judge(snap, legs, 0);

    // 귀환 검사: 마지막 도착(고현)이 예매한 귀환편 이전인가
    var resultLegs = new ArrayList<>(r.legs());
    var feasible = r.feasible();
    Integer lastArrive = null;
    for (var l : r.legs()) if (l.arriveMin() != null) lastArrive = l.arriveMin();
    int returnMin = TimeUtil.hhmmToMin(req.returnTime());
    if (feasible == Verdict.YES && lastArrive != null && lastArrive > returnMin) {
      resultLegs.add(new LegResult(Verdict.NO, null, null,
          "귀환 " + req.returnTime() + " 이전 고현 복귀 불가 (도착 "
              + TimeUtil.minToHHMM(lastArrive) + ")"));
      feasible = Verdict.NO;
    } else {
      resultLegs.add(new LegResult(feasible == Verdict.YES ? Verdict.YES : feasible,
          returnMin, null, null)); // 귀환편 탑승
    }
    return JudgeController.toRes(snap, new JudgeResult(feasible, List.copyOf(resultLegs)));
  }
}
