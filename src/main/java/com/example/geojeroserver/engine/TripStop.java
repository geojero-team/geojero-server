package com.example.geojeroserver.engine;

/**
 * 회차의 한 칸.
 *
 * {@code raw} 는 원문 셀 글자 그대로다. 판정·원문 조회는 쓰지 않고 스팟 계층(SpotLayer)만
 * 쓴다 — 경로가 한 칸에 문장으로 든 셀("…-대계(06:00)-외포-…")에서 시각을 꺼내기 위해서다.
 * 픽스처 JSON 에는 없어 null 로 들어온다.
 */
public record TripStop(String stop, StopStatus status, Integer departMin, String raw) {
  public TripStop(String stop, StopStatus status, Integer departMin) {
    this(stop, status, departMin, null);
  }
}
