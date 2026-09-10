package com.example.geojeroserver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 계약 v0 재현 검증 — 값은 전부 회귀에서 검증된 실데이터. */
@SpringBootTest
@AutoConfigureMockMvc
class ContractTest {
  @Autowired MockMvc mvc;

  @Test void 전_GET_엔드포인트_200() throws Exception {
    for (String url : new String[] {
        "/api/health", "/api/routes",
        "/api/routes/55/timetable?date=2026-09-09",
        "/api/stops/고현/departures?to=해금강&date=2026-09-09",
        "/api/pois", "/api/pois/1", "/api/courses",
        "/api/alerts?date=2026-09-09"}) {
      // /api/me·/api/saved-trips는 로그인 평면 — 비로그인 401 검증은 AuthTripsTest
      mvc.perform(get(url)).andExpect(status().isOk());
    }
  }

  /**
   * 화면 분류가 실제로 응답에 실리는지. 컬럼만 만들고 값이 안 들어가면 스팟 목록·카테고리 필터·
   * 지도 핀이 조용히 빈다 — 컴파일로도 DB 없는 테스트로도 안 잡히는 자리다.
   * 순서는 V3가 seq로 못박은 poi_id를 따른다(index = poi_id - 1).
   */
  @Test void POI목록_화면분류가_응답에_실린다() throws Exception {
    mvc.perform(get("/api/pois"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois.length()").value(13)) // V6 명사해수욕장 + V9 포로수용소
        .andExpect(jsonPath("$.pois[0].name").value("바람의언덕"))
        .andExpect(jsonPath("$.pois[0].shortName").value("바람의언덕"))
        .andExpect(jsonPath("$.pois[0].theme").value("VIEW"))
        .andExpect(jsonPath("$.pois[0].region").value("남부권"))
        .andExpect(jsonPath("$.pois[0].category").value("언덕·전망"))
        .andExpect(jsonPath("$.pois[0].lat").value(34.7440458))
        // 화면 이름(카드·핀 라벨). V7에서 "짧게"보다 "혼동 없게"로 옮겼다 —
        // '외도'는 섬, '학동'·'도장포'는 마을 이름이라 그 자체로 다른 것을 가리켰다.
        .andExpect(jsonPath("$.pois[1].shortName").value("도장포유람선"))
        .andExpect(jsonPath("$.pois[3].shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.pois[4].shortName").value("외도보타니아"))
        .andExpect(jsonPath("$.pois[4].category").value("식물원 · 유람선"))
        .andExpect(jsonPath("$.pois[6].theme").value("CASTLE"))
        .andExpect(jsonPath("$.pois[9].theme").value("GARDEN"))
        // Figma가 분류한 적 없는 POI는 null이다 — 없는 분류를 만들지 않는다(절대 규칙 1)
        .andExpect(jsonPath("$.pois[5].theme").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.pois[7].theme").value(org.hamcrest.Matchers.nullValue()))
        // V6 — 화면 목록에는 있는데 시드에 없어 사진도 좌표도 못 받던 곳.
        // contentId·좌표는 TourAPI searchKeyword2 실호출로 확인한 값이다.
        .andExpect(jsonPath("$.pois[11].name").value("명사해수욕장"))
        .andExpect(jsonPath("$.pois[11].theme").value("BEACH"))
        .andExpect(jsonPath("$.pois[11].region").value("남부권"))
        .andExpect(jsonPath("$.pois[11].lat").value(34.7272514))
        // V9 — 거제 9경 중 유일하게 고현(진입 관문)에 있는 스팟. 화면 스팟이 전부
        // 외곽이던 자리를 메운다. 사진은 대표·추가 7장이 전부 Type1이라 그대로 쓴다.
        .andExpect(jsonPath("$.pois[12].name").value("거제도포로수용소유적공원"))
        .andExpect(jsonPath("$.pois[12].shortName").value("포로수용소"))
        .andExpect(jsonPath("$.pois[12].tier").value("BEST"))
        .andExpect(jsonPath("$.pois[12].lat").value(34.8764184));
  }

  /** withImages는 POI마다 TourAPI를 부른다. 키가 없거나 실패해도 목록 자체는 성립해야 한다. */
  @Test void POI목록_withImages_는_실패해도_목록을_지키다() throws Exception {
    mvc.perform(get("/api/pois?withImages=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois.length()").value(13))
        .andExpect(jsonPath("$.pois[0].name").value("바람의언덕"));
  }

  @Test void 시간표_55번_고현발6회_막차1915_SKIP노출() throws Exception {
    mvc.perform(get("/api/routes/55/timetable?date=2026-09-09"))
        .andExpect(jsonPath("$.dayClass").value("WEEKDAY"))
        .andExpect(jsonPath("$.trips[?(@.direction==0)].direction").exists())
        .andExpect(jsonPath("$.trips[?(@.direction==0)]").isNotEmpty())
        // 고현발 방향 6회 (양방향 합산 12회 — direction으로 구분)
        .andExpect(jsonPath("$.trips[?(@.direction==0)].stops[?(@.stop=='고현')].time",
            org.hamcrest.Matchers.hasItems("06:25", "19:15")))
        .andExpect(jsonPath("$.trips[0].stops[?(@.stop=='장평')].status",
            org.hamcrest.Matchers.hasItem("SKIP")));
  }

  @Test void 출발조회_막차_1915() throws Exception {
    mvc.perform(get("/api/stops/고현/departures?to=해금강&date=2026-09-09"))
        .andExpect(jsonPath("$.departures.length()").value(6))
        .andExpect(jsonPath("$.lastDeparture").value("19:15"));
  }

  @Test void 판정_부산발_당일치기_YES() throws Exception {
    String body = """
        {"date":"2026-09-09","startTime":"06:50","legs":[
          {"type":"BUS","from":"부산사상","to":"고현"},
          {"type":"BUS","from":"고현","to":"해금강"},
          {"type":"BUS","from":"해금강","to":"고현"},
          {"type":"BUS","from":"고현","to":"부산사상","boardOnly":true}]}""";
    mvc.perform(post("/api/judge").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(jsonPath("$.feasible").value("YES"))
        .andExpect(jsonPath("$.legs[0].arrive").value("08:20"))
        .andExpect(jsonPath("$.alerts[?(@.stop=='홍포')].kind",
            org.hamcrest.Matchers.hasItem("DETOUR")));
  }

  @Test void 판정_여차_오후_NO_이유문장() throws Exception {
    String body = """
        {"date":"2026-09-09","startTime":"13:00","legs":[
          {"type":"BUS","from":"고현","to":"여차"}]}""";
    // 원문상 '여차'는 독립 열이 아니라 홍포 열의 주석(개행 포함 "12:50 (여차)")이라 정류소 격자에 없음
    // → "운행 없음"이 현 데이터의 정답. 주석 정류소 승격은 파서 백로그.
    mvc.perform(post("/api/judge").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(jsonPath("$.feasible").value("NO"))
        .andExpect(jsonPath("$.legs[0].reason",
            org.hamcrest.Matchers.containsString("운행 없음")));
  }

  @Test void 코스판정_부산발_YES_귀환검사포함() throws Exception {
    String body = """
        {"date":"2026-09-09","arrivalTime":"08:20","returnTime":"21:10"}""";
    mvc.perform(post("/api/courses/1/judge")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(jsonPath("$.feasible").value("YES"));
  }

  @Test void 코스판정_귀환시각_불가시_NO() throws Exception {
    String body = """
        {"date":"2026-09-09","arrivalTime":"08:20","returnTime":"10:00"}""";
    mvc.perform(post("/api/courses/1/judge")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(jsonPath("$.feasible").value("NO"))
        .andExpect(jsonPath("$.legs[-1].reason",
            org.hamcrest.Matchers.containsString("귀환")));
  }

  @Test void POI_상세_폴백형태() throws Exception {
    mvc.perform(get("/api/pois/1?lang=en"))
        .andExpect(jsonPath("$.name").value("바람의언덕"))
        .andExpect(jsonPath("$.langFallback").value(true))   // 매핑 seed 전이므로 국문 폴백
        .andExpect(jsonPath("$.detail.source").value("FALLBACK"))
        .andExpect(jsonPath("$.detail.reason").value("관광정보 확인 실패"))
        .andExpect(jsonPath("$.detail.checkedAt").exists());
  }

  @Test void 운영상태_우회2건() throws Exception {
    mvc.perform(get("/api/alerts?date=2026-09-09"))
        .andExpect(jsonPath("$.alerts[?(@.kind=='DETOUR')].stop",
            org.hamcrest.Matchers.hasItems("홍포", "명사")));
  }

  @Test void 스웨거_노출() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
