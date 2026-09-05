package com.example.geojeroserver.api;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    var basedOn = jdbc.queryForObject(
        """
        SELECT max(based_on)::text FROM timetable_versions
        WHERE source_file LIKE '%.xlsx'""", String.class); // BIS 표지 기준일 (seed 버전 제외)
    return Map.of("ok", true, "dataVersion", basedOn == null ? "미적재" : basedOn);
  }
}
