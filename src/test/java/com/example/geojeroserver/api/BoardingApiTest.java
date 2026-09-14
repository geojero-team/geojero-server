package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 타는 곳 지도 — `GET /api/pois/{id}/departures` 응답의 `boarding` (2026-09-14, 사용자 결정 · Figma 프레임 없음).
 *
 * 좌표·이름은 V26(TAGO 2026-09-13 원문)이고 정답은 geojero tests/jobs/test_boarding_output.py 가
 * 원문으로 다시 검산했다. 여기서는 **시간표 응답의 노선과 맞물려 나가는지**만 본다.
 *   매미성 → 고현 32번: 대표 대금교차로 GJB1599(213m), 20:37 편만 건너편 GJB1621(6m 떨어짐)
 *   김영삼 생가 → 고현 32번: 06:00 GJB283 · 20:55 GJB252 — 서로 반대편이라 대표 핀 없음
 *   고현터미널 → 바람의언덕: 55·55-1번 모두 터미널(일반) GJB500(0m)
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardingApiTest {
  @Autowired MockMvc mvc;

  private static final String D = "date=2026-09-14"; // 월요일 — V26 의 구간·노선 목록을 뽑은 날

  @Test void 매미성에서_고현은_대금교차로에서_타고_20시37분_편만_길_건너편이다() throws Exception {
    mvc.perform(get("/api/pois/7/departures?" + D))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boarding.from.name").value("매미성"))
        .andExpect(jsonPath("$.boarding.from.kind").value("SPOT"))
        .andExpect(jsonPath("$.boarding.from.lat").value(closeTo(34.9682131, 1e-7)))
        .andExpect(jsonPath("$.boarding.stops.length()").value(1))
        .andExpect(jsonPath("$.boarding.stops[0].nodeId").value("GJB1599"))
        .andExpect(jsonPath("$.boarding.stops[0].name").value("대금교차로"))
        .andExpect(jsonPath("$.boarding.stops[0].lat").value(34.9673158))
        .andExpect(jsonPath("$.boarding.stops[0].lng").value(128.7030327))
        .andExpect(jsonPath("$.boarding.stops[0].distanceM").value(213))
        .andExpect(jsonPath("$.boarding.stops[0].routes", hasItems("32", "33")))
        .andExpect(jsonPath("$.boarding.exceptions.length()").value(1))
        .andExpect(jsonPath("$.boarding.exceptions[0].routeNo").value("32"))
        .andExpect(jsonPath("$.boarding.exceptions[0].depart").value("20:37"))
        .andExpect(jsonPath("$.boarding.exceptions[0].nodeId").value("GJB1621"))
        .andExpect(jsonPath("$.boarding.exceptions[0].mainNodeId").value("GJB1599"))
        .andExpect(jsonPath("$.boarding.exceptions[0].gapM").value(6))
        .andExpect(jsonPath("$.boarding.exceptions[0].distanceM").value(218))
        .andExpect(jsonPath("$.boarding.unresolved.length()").value(0))
        // 화면 맨 아래 출처 줄 그대로(Figma 530:281 「정류소 좌표 국토교통부 TAGO · 2026-09-13」)
        .andExpect(jsonPath("$.boarding.source").value("정류소 좌표 국토교통부 TAGO · 2026-09-13"));
  }

  @Test void 예외_편은_그날_시간표에_있는_편과_맞물린다() throws Exception {
    mvc.perform(get("/api/pois/7/departures?" + D))
        .andExpect(jsonPath("$.departures[?(@.routeNo == '32' && @.depart == '20:37')]").isNotEmpty());
  }

  @Test void 김영삼_생가에서_고현_32번은_편마다_타는_쪽이_달라_대표_핀이_없다() throws Exception {
    mvc.perform(get("/api/pois/22/departures?" + D))
        .andExpect(jsonPath("$.byRoute[*].routeNo", contains("32")))
        .andExpect(jsonPath("$.boarding.stops.length()").value(0))
        .andExpect(jsonPath("$.boarding.exceptions[*].depart", contains("06:00", "20:55")))
        .andExpect(jsonPath("$.boarding.exceptions[*].nodeId", contains("GJB283", "GJB252")))
        .andExpect(jsonPath("$.boarding.exceptions[*].mainNodeId", everyItem(nullValue())))
        .andExpect(jsonPath("$.boarding.exceptions[*].gapM", everyItem(nullValue())))
        .andExpect(jsonPath("$.boarding.unresolved.length()").value(0));
  }

  @Test void 고현터미널에서_출발하면_타는_곳은_터미널이다() throws Exception {
    mvc.perform(get("/api/pois/1/departures?" + D + "&from=origin"))
        .andExpect(jsonPath("$.boarding.from.name").value("고현터미널"))
        .andExpect(jsonPath("$.boarding.from.kind").value("TERMINAL"))
        .andExpect(jsonPath("$.boarding.stops.length()").value(1))
        .andExpect(jsonPath("$.boarding.stops[0].nodeId").value("GJB500"))
        .andExpect(jsonPath("$.boarding.stops[0].name").value("터미널(일반)"))
        .andExpect(jsonPath("$.boarding.stops[0].lat").value(34.89061475))
        .andExpect(jsonPath("$.boarding.stops[0].distanceM").value(0));
  }

  @Test void 다음_스팟으로_가는_구간도_그_구간의_타는_곳을_준다() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D + "&toPoiId=3"))
        .andExpect(jsonPath("$.to.name").value("해금강"))
        .andExpect(jsonPath("$.boarding.from.name").value("학동몽돌해변"))
        .andExpect(jsonPath("$.boarding.stops[0].name").value("학동"))
        .andExpect(jsonPath("$.boarding.stops[0].routes", contains("55")));
  }

  @Test void 핀을_못_정한_노선은_이유와_함께_따로_준다() throws Exception {
    mvc.perform(get("/api/pois/17/departures?" + D))
        .andExpect(jsonPath("$.boarding.stops[0].nodeId").value("GJB111"))
        .andExpect(jsonPath("$.boarding.unresolved[?(@.routeNo == '37')].reason", contains("NO_STOP_NAME")));
  }

  /**
   * 거제씨월드 → 고현: 67-1·63번은 신촌 → 일운농협 → 지세포 순인데 화면 시각은 지세포 기준이다. 신촌에 핀을 꽂으면
   * 적힌 시각에 나가도 버스가 이미 지나갔다(2026-09-14 반박 검증) → 그 방향은 지세포에서 탄다. 22번처럼 지세포를 먼저 지나는 노선은 신촌 그대로.
   */
  @Test void 거제씨월드에서_시각보다_먼저_서는_신촌이_아니라_지세포에서_탄다() throws Exception {
    mvc.perform(get("/api/pois/18/departures?" + D))
        .andExpect(jsonPath("$.boardStop").value("지세포"))
        .andExpect(jsonPath("$.boarding.stops[?(@.routes anyof ['67-1'])].nodeId", contains("GJB849")))
        .andExpect(jsonPath("$.boarding.stops[?(@.routes anyof ['67-1'])].name", contains("지세포")))
        .andExpect(jsonPath("$.boarding.stops[?(@.routes anyof ['22'])].name", contains("신촌")));
  }

  @Test void 시각이_없는_구간은_타는_곳도_없다() throws Exception {
    mvc.perform(get("/api/pois/5/departures?" + D))
        .andExpect(jsonPath("$.emptyReason").value("NO_STOP_IN_TIMETABLE"))
        .andExpect(jsonPath("$.boarding").value(nullValue()));
  }
}
