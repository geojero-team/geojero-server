package com.example.geojeroserver.api;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 버전 노출. /api/health 는 controller/HealthController 가 갖는다 —
 * 같은 경로를 두 곳이 매핑하면 Ambiguous mapping 으로, 클래스 단순명이 겹치면
 * 빈 이름 충돌로 기동이 죽는다. 그래서 경로와 클래스명을 둘 다 비켜 둔다.
 */
@RestController
public class MetaController {
  private final JdbcTemplate jdbc;

  public MetaController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/meta")
  public Map<String, Object> meta() {
    var basedOn = jdbc.queryForObject(
        """
        SELECT max(based_on)::text FROM timetable_versions
        WHERE source_file LIKE '%.xlsx'""", String.class); // BIS 표지 기준일 (seed 버전 제외)
    return Map.of("ok", true, "dataVersion", basedOn == null ? "미적재" : basedOn);
  }
}
