package com.example.geojeroserver.tour;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 한도 방어 2층: api_calls 일일 카운터. 개발계정 1,000건/일 → soft 800에서 차단.
 * 운영계정(10만/일) 승인 후 상향. 자정(DB 기준) 자동 리셋 — day가 PK.
 */
@Component
public class JdbcCallCounter implements CallCounter {
  static final int SOFT_LIMIT = 800;

  private final JdbcTemplate jdbc;

  public JdbcCallCounter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean tryAcquire() {
    Integer c = jdbc.queryForObject("""
        INSERT INTO api_calls (day, count) VALUES (CURRENT_DATE, 1)
        ON CONFLICT (day) DO UPDATE SET count = api_calls.count + 1
        RETURNING count""", Integer.class);
    return c != null && c <= SOFT_LIMIT;
  }
}
