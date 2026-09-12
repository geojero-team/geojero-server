package com.example.geojeroserver.trips;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.engine.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
 * 일정 저장 — MVP 범위: 저장·목록·삭제만.
 *
 * 판정을 걷어냈다(2026-09-12 팀 결정). 그전에는 저장 직전 judge를 실행해 결과 전체를
 * verdict_at_save(jsonb NOT NULL)에 넣고, 목록을 읽을 때 다시 판정해 "저장 땐 성립 /
 * 지금은 불성립"을 비교할 수 있게 해뒀다. 제품이 판정을 내보내지 않기로 했으므로
 * 그 두 경로를 지웠다 —
 *   - verdict_at_save 는 **컬럼만 남긴다**(V15로 NOT NULL 해제). 기준문서 §6 컷 순서 1번이
 *     "컬럼은 남기고 UI만 컷"이라 정해둔 것과 같은 방향이다. 되살릴 때 스키마를 다시 만들 필요가 없다.
 *   - legs 경로도 지웠다. 그건 **사용자가 스팟을 골라 조립한 코스**를 판정하기 위한 것이었고,
 *     이제 코스는 우리가 짜서 내려준다. 컬럼(V10)은 같은 이유로 남긴다.
 *
 * 남은 저장 대상은 courseId 하나다.
 */
@RestController
public class SavedTripController {
  /** 저장은 코스를 가리킨다. 코스는 우리가 짜서 내려주므로 courseId 하나로 충분하다. */
  public record SaveReq(Long courseId, String travelDate, String arrivalTime, String returnTime) {}

  public record SavedTripRes(long savedTripId, Long courseId, String title, String chain,
      String travelDate, String arrivalTime, String returnTime) {}

  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

  private final JdbcTemplate jdbc;
  private final SessionCookies sessions;

  public SavedTripController(JdbcTemplate jdbc, SessionCookies sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @GetMapping("/api/saved-trips")
  public List<SavedTripRes> list(HttpServletRequest req) {
    long uid = sessions.require(req);
    return jdbc.query("""
        SELECT saved_trip_id, course_id, travel_date, arrival_time, return_time, title, chain
        FROM saved_trips WHERE user_id = ? ORDER BY travel_date, saved_trip_id""",
        (rs, i) -> toRes(rs), uid);
  }

  @PostMapping("/api/saved-trips")
  public ResponseEntity<SavedTripRes> save(HttpServletRequest req, @RequestBody SaveReq body) {
    long uid = sessions.require(req);

    if (body.courseId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId 가 필요하다");
    }
    // 코스는 이제 DB에 있다(V17 추천 코스 + CourseSeeder 검증 코스). 상수만 보면
    // 추천 코스 저장이 전부 400이 된다.
    var course = jdbc.queryForList("""
        SELECT course_name, summary, depart_time, return_time
        FROM courses WHERE course_id = ? AND enabled""", body.courseId());
    if (course.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 코스");
    }
    var c = course.get(0);

    LocalDate date;
    LocalTime arrival;
    LocalTime ret;
    try {
      date = LocalDate.parse(body.travelDate());
      // 출발·복귀 시각은 사용자가 고르는 값이 아니다 — 코스에 이미 박혀 있다.
      // 판정 시절엔 입력값이었고(막차 역산의 입력), 판정이 빠지고 코스를 우리가 짜서
      // 내려주면서 고를 자리가 없어졌다(02-2 코스 상세에 입력이 없다).
      // 보내오면 그 값을 쓰고(§3 검증 코스는 코스에 시각이 없다), 안 보내면 코스에서 채운다.
      arrival = body.arrivalTime() != null ? toTime(body.arrivalTime())
          : (LocalTime) sqlTime(c.get("depart_time"));
      ret = body.returnTime() != null ? toTime(body.returnTime())
          : (LocalTime) sqlTime(c.get("return_time"));
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "travelDate/arrivalTime/returnTime 형식 오류 (YYYY-MM-DD / HH:MM)");
    }
    if (arrival == null || ret == null) {
      // 비운 채 저장하면 「내 일정」이 이유 없는 빈칸을 그린다(절대규칙 3).
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "이 코스는 출발·복귀 시각이 없어 arrivalTime/returnTime 을 함께 보내야 한다");
    }

    String title = (String) c.get("course_name");
    String chain = (String) c.get("summary");

    long id = jdbc.queryForObject("""
        INSERT INTO saved_trips
          (user_id, course_id, travel_date, arrival_time, return_time, title, chain)
        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING saved_trip_id""", Long.class,
        uid, body.courseId(), date, arrival, ret, title, chain);

    return ResponseEntity.status(HttpStatus.CREATED).body(new SavedTripRes(
        id, body.courseId(), title, chain, date.toString(),
        HM.format(arrival), HM.format(ret)));
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
    return new SavedTripRes(rs.getLong("saved_trip_id"),
        rs.getObject("course_id", Long.class),
        rs.getString("title"), rs.getString("chain"), date.toString(),
        HM.format(arrival), HM.format(ret));
  }

  /** DB의 time 컬럼을 LocalTime 으로. 값이 없으면 null 그대로 둔다. */
  private static LocalTime sqlTime(Object o) {
    return o == null ? null : ((java.sql.Time) o).toLocalTime();
  }

  private static LocalTime toTime(String s) {
    int m = TimeUtil.hhmmToMin(s);
    return LocalTime.of(m / 60, m % 60);
  }
}
