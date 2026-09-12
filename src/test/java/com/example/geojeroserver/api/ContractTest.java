package com.example.geojeroserver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        .andExpect(jsonPath("$.pois.length()").value(22)) // 13 + V14 권역 스팟 9곳
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
        // V11에서 theme만 거두었다(9경 아님 + 우회로 코스 불성립 + 쓸 사진 없음).
        // 행은 남는다 — 우회가 풀리면 theme만 되돌리면 된다.
        .andExpect(jsonPath("$.pois[11].name").value("명사해수욕장"))
        .andExpect(jsonPath("$.pois[11].theme").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.pois[11].region").value("남부권"))
        .andExpect(jsonPath("$.pois[11].lat").value(34.7272514))
        // V9 — 거제 9경 중 유일하게 고현(진입 관문)에 있는 스팟. 화면 스팟이 전부
        // 외곽이던 자리를 메운다. 사진은 대표·추가 7장이 전부 Type1이라 그대로 쓴다.
        .andExpect(jsonPath("$.pois[12].name").value("거제도포로수용소유적공원"))
        .andExpect(jsonPath("$.pois[12].shortName").value("포로수용소"))
        .andExpect(jsonPath("$.pois[12].tier").value("BEST"))
        .andExpect(jsonPath("$.pois[12].lat").value(34.8764184));
  }

  /**
   * 화면이 말하는 스팟 수와 서버가 분류한 스팟 수가 같아야 한다.
   *
   * V4의 계약이 "화면은 theme 이 NULL 인 POI 를 목록에서 거른다"이므로, theme 가 붙은
   * 행 수가 곧 화면에 뜨는 스팟 수다. 답은 **17곳**이다 — 거제 9경 기준 8곳(V3~V11)에
   * 권역별 스팟 9곳(V14, 2026-09-12)을 더한 것이다.
   *
   * 숫자가 어긋나면 시드가 늘었거나 컷이 발동한 것이다. 어느 쪽이든
   * 기준문서를 먼저 고치고 이 숫자를 따라 고친다 — 반대 방향은 안 된다.
   */
  @Test void POI목록_화면에_뜨는_스팟은_17곳이다() throws Exception {
    // 8곳 → 17곳 (V14, 2026-09-12). 권역별 스팟 9곳을 더했다.
    // 판정을 걷어내고 '우리가 짠 코스'를 보여주기로 하면서 선정 기준이 바뀌었다 —
    // 전역 분산(판정에 유리)에서 권역별 뭉침(코스에 필요)으로. V14 주석 참고.
    mvc.perform(get("/api/pois"))
        .andExpect(status().isOk())
        // ?(@.theme) 는 '키가 있는가'만 보아 null 인 것까지 걸린다. null 비교여야 한다.
        .andExpect(jsonPath("$.pois[?(@.theme != null)]", org.hamcrest.Matchers.hasSize(17)));
  }

  /** 권역이 빠진 스팟이 있으면 카드에 '남부권 · 해수욕장' 자리가 비어 나간다. */
  @Test void 화면에_뜨는_스팟은_권역이_다섯_갈래로_채워져_있다() throws Exception {
    mvc.perform(get("/api/pois"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == null)]",
            org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == '남부권')]",
            org.hamcrest.Matchers.hasSize(5)))
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == '동부권')]",
            org.hamcrest.Matchers.hasSize(5)))
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == '북부권')]",
            org.hamcrest.Matchers.hasSize(3)))
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == '서부권')]",
            org.hamcrest.Matchers.hasSize(3)))
        .andExpect(jsonPath("$.pois[?(@.theme != null && @.region == '중부권')]",
            org.hamcrest.Matchers.hasSize(1)));
  }

  /** withImages는 POI마다 TourAPI를 부른다. 키가 없거나 실패해도 목록 자체는 성립해야 한다. */
  @Test void POI목록_withImages_는_실패해도_목록을_지키다() throws Exception {
    mvc.perform(get("/api/pois?withImages=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois.length()").value(22))
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

  /**
   * 판정(POST /api/judge, POST /api/courses/{id}/judge)은 제거됐다(2026-09-12).
   * 아래 셋은 그 엔드포인트가 지키던 **사실**을 조회 엔드포인트로 옮긴 것이다 —
   * 판정이 사라져도 원문 대조는 남는다(기준문서 §6 「컷 불가 바닥」의 '검증 게이트').
   */
  @Test void 출발조회_부산사상에서_고현_0820_도착() throws Exception {
    // §3 '부산발 당일치기'의 첫 구간. 07:00 출발 → 08:20 도착이 앵커다.
    mvc.perform(get("/api/stops/부산사상/departures?to=고현&date=2026-09-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departures[0].depart").value("07:00"))
        .andExpect(jsonPath("$.departures[0].arrive").value("08:20"));
  }

  @Test void 출발조회_해금강발_복귀_1848과_막차_2005() throws Exception {
    // §2 복귀표. 도장포 막배 18:20 복귀 뒤 탈 수 있는 버스가 18:48이고 막차가 20:05다.
    mvc.perform(get("/api/stops/해금강/departures?to=고현&date=2026-09-09&after=18:20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departures[0].depart").value("18:48"))
        .andExpect(jsonPath("$.lastDeparture").value("20:05"));
  }

  @Test void 출발조회_여차는_격자에_없어_빈_결과다() throws Exception {
    // 원문상 '여차'는 독립 열이 아니라 홍포 열의 주석("12:50 (여차)")이라 정류소 격자에 없다.
    // 빈 결과가 현 데이터의 정답이고, **없는 시각을 만들지 않는다**(절대규칙 1).
    // 주석 정류소 승격은 파서 백로그.
    mvc.perform(get("/api/stops/고현/departures?to=여차&date=2026-09-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departures").isEmpty())
        .andExpect(jsonPath("$.lastDeparture").doesNotExist());
  }

  @Test void 운영상태_알림은_조회에서_확인한다() throws Exception {
    // 전에는 판정 응답이 alerts를 함께 실어 보냈다. 이제 전용 엔드포인트로만 본다.
    mvc.perform(get("/api/alerts?date=2026-09-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alerts[?(@.stop=='홍포')].kind",
            org.hamcrest.Matchers.hasItem("DETOUR")));
  }

  /**
   * `/api/meta`가 세션 서명키 설정 여부를 답한다 — 값이 아니라 예/아니오만.
   * 배포된 서버가 재배포마다 전원 로그아웃되는 상태인지 밖에서 확인하는 유일한 창이다.
   */
  @Test void meta_가_세션키_설정여부를_알린다() throws Exception {
    mvc.perform(get("/api/meta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionKey",
            org.hamcrest.Matchers.isOneOf("configured", "temporary")));
  }

  /**
   * 공휴일이 평일로 판정되면 그날 답이 통째로 틀린다 — 남부면 마을버스는 휴일 전면 운휴다
   * (기준문서 §2). 토·일은 날짜에서 알아내므로, holidays 표가 실제로 일하는 건
   * **평일에 걸린 공휴일**뿐이다. 2026-09-25는 금요일이지만 추석이다.
   *
   * 9/28(월)을 평일로 못박아 둔 것은 의도적이다. 오늘(2026-09-11) 기준 임시공휴일이
   * 아니지만 국무회의로 지정될 수 있고, 지정되면 심사 구간 한복판이다.
   * **지정되면 이 테스트가 깨진다 — 그때 V13 다음 마이그레이션으로 넣으라는 신호다.**
   */
  @Test void 공휴일은_평일이_아니라_휴일로_분류된다() throws Exception {
    // 판정 제거로 통로만 바꿨다(POST /api/judge → GET timetable). 지키는 것은 그대로다 —
    // dayClass 는 시간표 응답에도 실려 나온다.
    mvc.perform(get("/api/routes/55/timetable?date=2026-09-25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dayClass").value("HOLIDAY"));

    mvc.perform(get("/api/routes/55/timetable?date=2026-09-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dayClass").value("WEEKDAY"));
  }

  /**
   * 휴일에 남부면 마을버스가 정말로 빠지는가 — §2 "마을버스 남부면 전 노선 휴일 운휴".
   * dayClass 만 맞고 시간표가 그대로면 공휴일 표가 일을 안 하는 것이다.
   */
  @Test void 추석에는_남부면_마을버스가_빠지고_55번은_그대로다() throws Exception {
    mvc.perform(get("/api/routes/남부1/timetable?date=2026-09-25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trips").isEmpty());

    mvc.perform(get("/api/routes/55/timetable?date=2026-09-25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trips[?(@.direction==0)].stops[?(@.stop=='고현')].time",
            org.hamcrest.Matchers.hasItems("06:25", "19:15")));
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
