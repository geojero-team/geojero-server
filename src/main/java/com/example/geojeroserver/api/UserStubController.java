package com.example.geojeroserver.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 평면 계약 스텁 — 카카오 OAuth·saved_trips 실구현은 후속 태스크.
 * 비로그인 판정 경로는 이 컨트롤러를 전혀 거치지 않는다.
 */
@RestController
public class UserStubController {
  private static final Map<String, Object> SAMPLE_TRIP = Map.of(
      "savedTripId", 1, "courseId", 1, "travelDate", "2026-09-13",
      "arrivalTime", "08:20", "returnTime", "21:10",
      "verdictAtSave", Map.of("feasible", "YES", "dayClass", "HOLIDAY"));

  @PostMapping("/api/auth/kakao")
  public Map<String, Object> kakao() {
    return Map.of("ok", true, "nickname", "목유저");
  }

  @GetMapping("/api/me")
  public Map<String, Object> me() {
    return Map.of("nickname", "목유저");
  }

  @GetMapping("/api/saved-trips")
  public List<Map<String, Object>> list() {
    return List.of(SAMPLE_TRIP);
  }

  @PostMapping("/api/saved-trips")
  public ResponseEntity<Map<String, Object>> save() {
    return ResponseEntity.status(HttpStatus.CREATED).body(SAMPLE_TRIP);
  }

  @DeleteMapping("/api/saved-trips/{id}")
  public ResponseEntity<Void> delete() {
    return ResponseEntity.noContent().build();
  }
}
