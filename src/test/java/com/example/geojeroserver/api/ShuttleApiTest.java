package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 도선 시간표(V31, 2026-09-15 사용자 입력) — `GET /api/pois/{id}/ferries` 의 shuttles.
 * 값은 사용자가 옮겨 준 운항사 안내 원문이다. 스팟 id 는 tour_content_id 로 찾는다(시드 순서가 환경마다 다를 수 있다).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShuttleApiTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private static final String MONDAY = "2026-09-14";
  private static final String SATURDAY = "2026-09-19";

  private long poi(String contentId) {
    return jdbc.queryForObject("SELECT poi_id FROM pois WHERE tour_content_id = ?", Long.class, contentId);
  }

  /** 지심도 — 「평일, 주말 동일」. 버스 정류장이 없어 외도처럼 배만 있고, 외도 유람선 표(ferries)는 비어 있다. */
  @Test void 지심도_도선은_평일_주말_같은_시각이다() throws Exception {
    long jisimdo = poi("128035");
    for (String date : List.of(MONDAY, SATURDAY)) {
      mvc.perform(get("/api/pois/" + jisimdo + "/ferries?date=" + date))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hasBusStop").value(false))
          .andExpect(jsonPath("$.ferries", empty()))
          .andExpect(jsonPath("$.shuttles[0].dockName").value("장승포"))
          .andExpect(jsonPath("$.shuttles[0].islandName").value("지심도"))
          .andExpect(jsonPath("$.shuttles[0].inTimes", contains("08:30", "10:30", "12:30", "14:30", "16:30")))
          .andExpect(jsonPath("$.shuttles[0].outTimes", contains("08:50", "10:50", "12:50", "14:50", "16:50")))
          .andExpect(jsonPath("$.shuttles[0].bookingUrl").value("https://booking.naver.com/booking/12/bizes/56835?area=bns"))
          .andExpect(jsonPath("$.shuttles[0].notice").value("성수기 미예약 시, 탑승이 어려울 수 있습니다."));
    }
  }

  /**
   * 내도(스팟 공곶이·내도) — 주중에만 시각이 있고 막배는 나오는 배가 20분 빠르다(17:10).
   * 주말 · 공휴일은 시각이 아니라 「5번~8번 (주말 수시운행)」이라 목록이 비고 원문이 이유다 — 평일 시각을 쓰지 않는다.
   */
  @Test void 내도_도선은_평일만_시각이_있고_주말은_수시운행() throws Exception {
    long naedo = poi("2536196");
    mvc.perform(get("/api/pois/" + naedo + "/ferries?date=" + MONDAY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shuttles[0].dockName").value("구조라"))
        .andExpect(jsonPath("$.shuttles[0].islandName").value("내도"))
        .andExpect(jsonPath("$.shuttles[0].dayClass").value("WEEKDAY"))
        .andExpect(jsonPath("$.shuttles[0].inTimes", contains("09:00", "11:00", "13:00", "15:00", "17:00")))
        .andExpect(jsonPath("$.shuttles[0].outTimes", contains("09:30", "11:30", "13:30", "15:30", "17:10")))
        .andExpect(jsonPath("$.shuttles[0].tripNote").value("관광시간 10분"))
        .andExpect(jsonPath("$.shuttles[0].holidayNote").value("5번~8번 (주말 수시운행)"));

    mvc.perform(get("/api/pois/" + naedo + "/ferries?date=" + SATURDAY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shuttles[0].dayClass").value("HOLIDAY"))
        .andExpect(jsonPath("$.shuttles[0].inTimes", empty()))
        .andExpect(jsonPath("$.shuttles[0].outTimes", empty()))
        .andExpect(jsonPath("$.shuttles[0].holidayNote").value("5번~8번 (주말 수시운행)"));
  }

  /**
   * 선착장 좌표(V32) — 배 칩의 「타는 곳」 지도 카드. TourAPI 등록값(와현 2776287 · 지심도 터미널 2756617) 또는
   * 운항사 주소의 카카오 주소 검색값(구조라 도선). numeric(10,7)로 반올림된 값이다.
   */
  @Test void 선착장_좌표가_실린다() throws Exception {
    mvc.perform(get("/api/pois/" + poi("128035") + "/ferries?date=" + MONDAY))
        .andExpect(jsonPath("$.shuttles[0].dockLat").value(34.8673157))
        .andExpect(jsonPath("$.shuttles[0].dockLng").value(128.7276451)); // 128.7276450720 → 7자리 반올림
    mvc.perform(get("/api/pois/" + poi("2536196") + "/ferries?date=" + MONDAY))
        .andExpect(jsonPath("$.shuttles[0].dockLat").value(34.8063339));
    mvc.perform(get("/api/pois/" + poi("126581") + "/ferries?date=" + MONDAY))
        .andExpect(jsonPath("$.ferries[?(@.dock.dockCode == 'WAHYEON')].dock.lat", contains(34.8119048)))
        .andExpect(jsonPath("$.ferries[?(@.dock.dockCode == 'JISEPO')].dock.lng", contains(128.7036132)));
  }

  /** 도선이 생겼으니 시간표 준비 중이 아니다(외도처럼 배가 답). 목록 둘째 줄 · 스팟 상세 「선착장에서 타요」에 선착장이 실린다. */
  @Test void 도선이_있으면_준비중이_아니고_목록에_선착장이_실린다() throws Exception {
    long naedo = poi("2536196");
    mvc.perform(get("/api/pois/" + naedo + "/departures?date=" + MONDAY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emptyReason").value("NO_STOP_IN_TIMETABLE"));

    mvc.perform(get("/api/pois"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois[?(@.shortName == '공곶이·내도')].ferryDocks[0]", contains("구조라")))
        .andExpect(jsonPath("$.pois[?(@.shortName == '지심도')].ferryDocks[0]", contains("장승포")))
        // 외도 유람선 선착장 순서는 그대로다
        .andExpect(jsonPath("$.pois[?(@.shortName == '외도보타니아')].ferryDocks[0]", contains("도장포")));
  }
}
