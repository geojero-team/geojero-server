package com.example.geojeroserver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 스팟 시간표 — 02-2의 「스팟 시간표」 3화면(453:210 · 453:288 · 453:415)과 시간표 탭.
 *
 * 값은 전부 라이브 실측(2026-09-14 평일)이다. 시간표가 재조사로 교체되면 여기가 깨지고,
 * 그때 원문과 대조해 고친다 — 숫자만 맞추면 게이트가 죽는다(기준문서 §6).
 *
 * 2026-09-13: 스팟 시간표가 스팟 계층(SpotLayer)을 읽게 되며 값이 바뀐 곳은 전부 팀원 산출물
 * spot_times.json(원본 xlsx 재생성으로 대조)과 같아진 것이다 — 학동→고현 13→14회는 60번대 시트
 * 24행 경로 문장의 67-1번 "학동(15:30)-…-고현(16:17)", 고현→학동 12→13회는 50번대 시트 6행 67번
 * "고현(5:40)-…-학동(6:20)"이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpotTimetableApiTest {
  @Autowired MockMvc mvc;

  private static final String D = "date=2026-09-14"; // 월요일

  // ── 스팟 기준 조회 (453:288 학동몽돌해변 → 고현) ───────────────────────────

  /** "학동 정류장에서 타요." — 스팟이 아니라 정류장에서 타는데, 그 이름이 응답에 있어야 한다. */
  @Test void 스팟시간표_타는곳이_응답에_있다() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poiId").value(4))
        .andExpect(jsonPath("$.shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.boardStop").value("학동"))
        .andExpect(jsonPath("$.alightLabel").value("학동 정류장"))
        .andExpect(jsonPath("$.boardStopDiffers").value(false))
        .andExpect(jsonPath("$.dayClass").value("WEEKDAY"))
        .andExpect(jsonPath("$.to.name").value("고현터미널"));
  }

  /** to를 생략하면 고현터미널이다 — 모든 코스의 복귀 지점이라 기본값으로 쓴다. */
  @Test void 스팟시간표_첫차_막차_횟수() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D))
        .andExpect(jsonPath("$.count").value(14))   // 67-1번 15:30 은 경로 문장에서 온다
        .andExpect(jsonPath("$.firstDeparture").value("07:35"))
        .andExpect(jsonPath("$.lastDeparture").value("20:15"))
        .andExpect(jsonPath("$.departures[0].routeNo").value("67-1"))
        .andExpect(jsonPath("$.departures[0].depart").value("07:35"))
        .andExpect(jsonPath("$.departures[0].arrive").value("08:33"));
  }

  /**
   * ★ 노선별로 소요시간을 갈라 준다. 섞어 평균을 내면 실제로 운행하지 않는 값이 나온다
   * (`.claude/rules/engine.md`). 화면의 "고현터미널까지 약 40분 · 67-1번은 약 50분"이 이것이다.
   *
   * 그리고 **같은 노선·같은 방향인데도 소요시간이 흔들린다** — 67·67-1이 50번대와 60번대
   * 시트에 같은 회차로 두 번 실려 2~5분 다르기 때문이다(팀원 spot_times warnings).
   * 그래서 최댓값과 최솟값을 함께 준다. 화면은 늦은 쪽을 써야 버스를 놓치지 않는다.
   */
  @Test void 스팟시간표_노선별_소요시간을_갈라_준다() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D))
        .andExpect(jsonPath("$.byRoute.length()").value(2))
        .andExpect(jsonPath("$.byRoute[0].routeNo").value("55"))
        .andExpect(jsonPath("$.byRoute[0].count").value(6))
        .andExpect(jsonPath("$.byRoute[0].durationMin").value(43))    // 늦은 쪽
        .andExpect(jsonPath("$.byRoute[0].durationMinLow").value(40))
        .andExpect(jsonPath("$.byRoute[0].durationVaries").value(true))
        .andExpect(jsonPath("$.byRoute[1].routeNo").value("67-1"))
        .andExpect(jsonPath("$.byRoute[1].count").value(8))
        .andExpect(jsonPath("$.byRoute[1].durationMin").value(58))
        .andExpect(jsonPath("$.byRoute[1].durationMinLow").value(47)); // 15:30>16:17 (경로 문장)
  }

  /** 반대 방향은 흔들리지 않는다 — 55번 고현→학동은 6회 전부 40분(기준문서 §2). */
  @Test void 스팟시간표_고현에서_학동은_40분_상수다() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D + "&from=origin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(13))   // 67번 05:40 은 경로 문장 "고현(5:40)-…-학동(6:20)"
        .andExpect(jsonPath("$.byRoute[0].routeNo").value("55"))
        .andExpect(jsonPath("$.byRoute[0].durationMin").value(40))
        .andExpect(jsonPath("$.byRoute[0].durationMinLow").value(40))
        .andExpect(jsonPath("$.byRoute[0].durationVaries").value(false));
  }

  /** "다음 버스 13:00 · 55번" — 지금 시각 이후 첫차. */
  @Test void 스팟시간표_다음버스() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D + "&after=12:30"))
        .andExpect(jsonPath("$.next.routeNo").value("55"))
        .andExpect(jsonPath("$.next.depart").value("13:00"))
        .andExpect(jsonPath("$.next.durationMin").value(40));
  }

  /** 목적지를 스팟으로 줄 수 있다 — 학동 → 해금강은 55번 6회(기준문서 §2). */
  @Test void 스팟시간표_목적지를_스팟으로_준다() throws Exception {
    mvc.perform(get("/api/pois/4/departures?" + D + "&toPoiId=3"))
        .andExpect(jsonPath("$.to.poiId").value(3))
        .andExpect(jsonPath("$.to.stop").value("해금강"))
        .andExpect(jsonPath("$.count").value(6))
        .andExpect(jsonPath("$.byRoute.length()").value(1))
        .andExpect(jsonPath("$.byRoute[0].routeNo").value("55"))
        .andExpect(jsonPath("$.byRoute[0].durationMin").value(10));
  }

  /**
   * ★ 내리는 정류장과 시간표를 읽는 정류장이 다른 경우를 숨기지 않는다.
   * 조선해양문화관·씨월드는 **신촌**에서 내리지만 시간표에 신촌 칸이 없어 **지세포** 시각을 쓴다.
   * 화면이 이 사실을 함께 말해야 한다 — 안 그러면 "지세포 시간표"를 "신촌 시간표"라고 거짓말한다.
   */
  @Test void 스팟시간표_내리는곳과_읽는곳이_다르면_알려준다() throws Exception {
    mvc.perform(get("/api/pois/20/departures?" + D))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortName").value("조선해양문화관"))
        .andExpect(jsonPath("$.boardStop").value("지세포"))
        .andExpect(jsonPath("$.alightLabel").value("신촌 정류장"))
        .andExpect(jsonPath("$.boardStopDiffers").value(true))
        .andExpect(jsonPath("$.count").value(45));   // 격자만 읽던 31회 + 원문의 '터미널' 칸(=고현)
  }

  /**
   * ★★ 정류장이 정해지지 않은 스팟 — 외도보타니아는 배로만 가서 버스 정류장이 없다.
   * 빈 목록을 주면서 **왜 비었는지**를 함께 줘야 한다. 이유 없는 빈칸은 우리가
   * §4에서 비판한 것이다(절대규칙 3).
   */
  @Test void 스팟시간표_정류장칸이_없으면_이유를_준다() throws Exception {
    mvc.perform(get("/api/pois/5/departures?" + D))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardStop").doesNotExist())
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.emptyReason").value("NO_STOP_IN_TIMETABLE"));
  }

  /**
   * ★ 격자에 칸이 없던 스팟 정류장이 이제 시간표를 낸다 — 앞뒤 정류장 시각으로 감싼 값이라
   * 전부 추정이다. 매미성(대금교차로)은 32·33번이 외포 바로 앞에서 선다(spot_times.json 과 같은 16회).
   */
  @Test void 격자에_칸이_없던_매미성은_감싼_시각으로_낸다() throws Exception {
    mvc.perform(get("/api/pois/7/departures?" + D))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardStop").value("대금교차로"))
        .andExpect(jsonPath("$.alightLabel").value("대금교차로 정류장"))
        .andExpect(jsonPath("$.boardStopDiffers").value(false))
        .andExpect(jsonPath("$.count").value(16))
        .andExpect(jsonPath("$.emptyReason").doesNotExist())
        .andExpect(jsonPath("$.departures[*].estimated")
            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
  }

  /**
   * ★ 코스와 시간표가 같은 말을 한다 — 3-01 의 "해금강 → 바람의언덕 55번 · 12분"(16:38>16:50)이
   * 스팟 시간표에도 있다. 전에는 격자에 도장포가 없어 "운행 없음"이었다.
   */
  @Test void 해금강에서_바람의언덕은_55번_12분이다() throws Exception {
    mvc.perform(get("/api/pois/3/departures?" + D + "&toPoiId=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.to.stop").value("도장포"))
        .andExpect(jsonPath("$.count").value(6))
        .andExpect(jsonPath("$.byRoute[0].routeNo").value("55"))
        .andExpect(jsonPath("$.byRoute[0].durationMin").value(12))
        .andExpect(jsonPath("$.departures[?(@.depart=='16:38')].arrive")
            .value(org.hamcrest.Matchers.contains("16:50")))
        .andExpect(jsonPath("$.departures[0].estimated").value(true));
  }

  /**
   * 김영삼 생가(대계): 32번 2회 + 급행 2000번 39회(V19). 2000번 대계 도착은 원문에 없어
   * 고현 출발 + 60분(사용자 제공)이고 추정으로 표시한다.
   */
  @Test void 김영삼생가는_2000번_포함_41회다() throws Exception {
    mvc.perform(get("/api/pois/22/departures?" + D + "&from=origin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(41))
        .andExpect(jsonPath("$.byRoute[?(@.routeNo=='2000')].count")
            .value(org.hamcrest.Matchers.contains(39)))
        .andExpect(jsonPath("$.byRoute[?(@.routeNo=='2000')].durationMin")
            .value(org.hamcrest.Matchers.contains(60)))
        .andExpect(jsonPath("$.departures[?(@.routeNo=='2000')].estimated")
            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
  }

  @Test void 스팟시간표_없는_스팟은_404() throws Exception {
    mvc.perform(get("/api/pois/9999/departures?" + D)).andExpect(status().isNotFound());
  }

  // ── 세 갈래: 정류소 기준 조회에도 이유를 붙인다 ─────────────────────────────

  /**
   * ★★★ 이 프로젝트의 명제 그 자체다.
   *
   * 평일 도장포→학동은 남부2가 **실제로 선다**(원문이 기점 시각만 줘서 `[미확인]`).
   * 일요일은 노선 자체가 운휴라 **정말로 없다**. 전에는 두 응답이 글자 하나 안 달랐다 —
   * 화면이 구분할 방법이 없어 "운행 없음"으로 뭉갤 수밖에 없었다.
   */
  @Test void 운행없음과_시각미상을_응답에서_갈라_말한다() throws Exception {
    mvc.perform(get("/api/stops/도장포/departures?to=학동&date=2026-09-14")) // 월요일
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departures.length()").value(0))
        .andExpect(jsonPath("$.emptyReason").value("UNKNOWN_TIME"))
        .andExpect(jsonPath("$.unknownTimeRoutes")
            .value(org.hamcrest.Matchers.contains("남부2")));

    mvc.perform(get("/api/stops/도장포/departures?to=학동&date=2026-09-13")) // 일요일
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departures.length()").value(0))
        .andExpect(jsonPath("$.emptyReason").value("NO_SERVICE"))
        .andExpect(jsonPath("$.unknownTimeRoutes.length()").value(0));
  }

  /** 결과가 있으면 이유가 없다 — 세 갈래의 첫 번째. */
  @Test void 운행이_있으면_emptyReason은_없다() throws Exception {
    mvc.perform(get("/api/stops/고현/departures?to=해금강&date=2026-09-14"))
        .andExpect(jsonPath("$.departures.length()").value(6))
        .andExpect(jsonPath("$.emptyReason").doesNotExist());
  }

  /** 여차는 정류소 격자에 아예 없다 — 정차 사실도 없으므로 "운행 없음"이다(기준문서 §2). */
  @Test void 여차는_격자에_없어_운행없음이다() throws Exception {
    mvc.perform(get("/api/stops/여차/departures?to=고현&date=2026-09-14"))
        .andExpect(jsonPath("$.departures.length()").value(0))
        .andExpect(jsonPath("$.emptyReason").value("NO_SERVICE"));
  }
}
