package com.example.geojeroserver.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * COURSES 상수를 courses 테이블에 동기화 — saved_trips.course_id FK의 원천.
 * 멱등 upsert라 기동마다 실행해도 안전. (코스 정의는 읽기 경로 소유 콘텐츠 —
 * BIS 파이프라인 seed와 분리한다는 규칙에 따라 마이그레이션이 아닌 여기서.)
 */
@Component
public class CourseSeeder implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  public CourseSeeder(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (var c : CourseController.COURSES) {
      jdbc.update("""
          INSERT INTO courses (course_id, course_name, summary, theme)
          VALUES (?, ?, ?, ?::course_theme)
          ON CONFLICT (course_id) DO UPDATE
            SET course_name = EXCLUDED.course_name,
                summary = EXCLUDED.summary,
                theme = EXCLUDED.theme""",
          c.courseId(), c.name(), c.summary(), c.theme());
    }
    jdbc.execute("SELECT setval('courses_course_id_seq', (SELECT MAX(course_id) FROM courses))");
  }
}
