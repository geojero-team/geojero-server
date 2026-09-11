package com.example.geojeroserver.api;

import com.example.geojeroserver.auth.SessionCookies;
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
  private final SessionCookies sessions;

  public MetaController(JdbcTemplate jdbc, SessionCookies sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @GetMapping("/api/meta")
  public Map<String, Object> meta() {
    var basedOn = jdbc.queryForObject(
        """
        SELECT max(based_on)::text FROM timetable_versions
        WHERE source_file LIKE '%.xlsx'""", String.class); // BIS 표지 기준일 (seed 버전 제외)
    return Map.of(
        "ok", true,
        "dataVersion", basedOn == null ? "미적재" : basedOn,
        // 세션 서명키가 주입됐는가. temporary 면 **재배포마다 전 사용자가 로그아웃된다**.
        // 값은 내보내지 않는다 — 설정 여부만이다. 이걸 밖에서 못 보면 EC2에 붙을 수 있는
        // 사람만 답할 수 있고, 실제로 그 때문에 한 번 잘못 단정했다(2026-09-11).
        "sessionKey", sessions.isSecretConfigured() ? "configured" : "temporary");
  }
}
