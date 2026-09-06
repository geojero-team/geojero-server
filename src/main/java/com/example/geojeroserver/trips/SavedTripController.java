package com.example.geojeroserver.trips;

import com.example.geojeroserver.api.CourseJudgeService;
import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.engine.TimeUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 일정 저장 — MVP 범위: 저장·목록·삭제·재판정만 (상태 변경·자동 전환·공유·알림은 버림).
 * 저장 직전 judge를 실행해 결과 전체를 verdict_at_save(jsonb, NOT NULL)에 넣는다 —
 * 시간표가 개편돼도 저장 시점 판정은 불변으로 남는다.
 */
@RestController
public class SavedTripController {
  public record SaveReq(Long courseId, String travelDate, String arrivalTime, String returnTime) {}
  public record SavedTripRes(long savedTripId, long courseId, String travelDate,
      String arrivalTime, String returnTime, Map<String, Object> verdictAtSave,
      Map<String, Object> verdictNow) {}

  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final CourseJudgeService courseJudge;
  private final SessionCookies sessions;
  private final ObjectMapper om = new ObjectMapper();

  public SavedTripController(JdbcTemplate jdbc, CourseJudgeService courseJudge,
      SessionCookies sessions) {
    this.jdbc = jdbc;
    this.courseJudge = courseJudge;
    this.sessions = sessions;
  }

  @GetMapping("/api/saved-trips")
  public List<SavedTripRes> list(HttpServletRequest req) {
    long uid = sessions.require(req);
    return jdbc.query("""
        SELECT saved_trip_id, course_id, travel_date, arrival_time, return_time, verdict_at_save
        FROM saved_trips WHERE user_id = ? ORDER BY travel_date, saved_trip_id""",
        (rs, i) -> toRes(rs), uid);
  }

  @PostMapping("/api/saved-trips")
  public ResponseEntity<SavedTripRes> save(HttpServletRequest req, @RequestBody SaveReq body)
      throws Exception {
    long uid = sessions.require(req);
    if (body.courseId() == null || !courseJudge.hasCourse(body.courseId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "courseId가 없거나 알 수 없는 코스");
    }
    LocalDate date;
    LocalTime arrival;
    LocalTime ret;
    try {
      date = LocalDate.parse(body.travelDate());
      arrival = toTime(body.arrivalTime());
      ret = toTime(body.returnTime());
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "travelDate/arrivalTime/returnTime 형식 오류 (YYYY-MM-DD / HH:MM)");
    }
    // 저장 직전 판정 — 판정 없는 저장은 스키마(NOT NULL)로도 막혀 있다
    Map<String, Object> verdict = om.convertValue(
        courseJudge.judge(body.courseId(), date.toString(), HM.format(arrival), HM.format(ret)),
        MAP_TYPE);
    long id = jdbc.queryForObject("""
        INSERT INTO saved_trips
          (user_id, course_id, travel_date, arrival_time, return_time, verdict_at_save)
        VALUES (?, ?, ?, ?, ?, ?::jsonb) RETURNING saved_trip_id""", Long.class,
        uid, body.courseId(), date, arrival, ret, om.writeValueAsString(verdict));
    return ResponseEntity.status(HttpStatus.CREATED).body(new SavedTripRes(
        id, body.courseId(), date.toString(), HM.format(arrival), HM.format(ret),
        verdict, verdict));
  }

  @DeleteMapping("/api/saved-trips/{id}")
  public ResponseEntity<Void> delete(HttpServletRequest req, @PathVariable long id) {
    long uid = sessions.require(req);
    int n = jdbc.update("DELETE FROM saved_trips WHERE saved_trip_id = ? AND user_id = ?",
        id, uid);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "저장 일정 없음");
    }
    return ResponseEntity.noContent().build();
  }

  private SavedTripRes toRes(ResultSet rs) throws SQLException {
    LocalDate date = rs.getObject("travel_date", LocalDate.class);
    LocalTime arrival = rs.getObject("arrival_time", LocalTime.class);
    LocalTime ret = rs.getObject("return_time", LocalTime.class);
    Map<String, Object> saved;
    try {
      saved = om.readValue(rs.getString("verdict_at_save"), MAP_TYPE);
    } catch (Exception e) {
      throw new SQLException("verdict_at_save 역직렬화 실패", e);
    }
    // 재판정: 현재 데이터로 다시 계산. 실패해도 목록은 성립 — 저장본을 그대로 노출
    Map<String, Object> now;
    try {
      now = om.convertValue(courseJudge.judge(rs.getLong("course_id"),
          date.toString(), HM.format(arrival), HM.format(ret)), MAP_TYPE);
    } catch (RuntimeException e) {
      now = null;
    }
    return new SavedTripRes(rs.getLong("saved_trip_id"), rs.getLong("course_id"),
        date.toString(), HM.format(arrival), HM.format(ret), saved, now);
  }

  private static LocalTime toTime(String s) {
    int m = TimeUtil.hhmmToMin(s);
    return LocalTime.of(m / 60, m % 60);
  }
}
