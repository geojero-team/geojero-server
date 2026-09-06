package com.example.geojeroserver.api;

import java.util.List;
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

  /** §3 검증 코스 3종 — 확정 데이터 기반 상수. DB 동기화는 CourseSeeder(saved_trips FK 원천). */
  static final List<CourseDto> COURSES = List.of(
      new CourseDto(1, "부산발 당일치기", "ISLAND",
          "사상 07:00 → 바람의언덕·유람선 → 막차 복귀 → 부산행 21:10"),
      new CourseDto(2, "서울발 무박 일출", "NATURE",
          "서울남부 23:30 → 첫차 06:25 → 일출 → 상행 22:00"),
      new CourseDto(3, "외도 풀코스", "ISLAND",
          "도장포 막배 15:30 → 18:20 복귀 → 버스 연결"));

  private final CourseJudgeService judge;

  public CourseController(CourseJudgeService judge) {
    this.judge = judge;
  }

  @GetMapping("/api/courses")
  public CoursesRes courses() {
    return new CoursesRes(COURSES);
  }

  @PostMapping("/api/courses/{courseId}/judge")
  public JudgeController.JudgeRes judgeCourse(@PathVariable long courseId,
      @RequestBody CourseJudgeReq req) {
    return judge.judge(courseId, req.date(), req.arrivalTime(), req.returnTime());
  }
}
