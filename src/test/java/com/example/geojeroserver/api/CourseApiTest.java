package com.example.geojeroserver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 코스 API 계약 — 02-2 화면(Figma 442:748)이 이 응답만으로 그려져야 한다.
 *
 * 값은 전부 V17 적재분(팀원 산출물, BIS 원문 2026-08-18 평일)이고 CourseDataTest가
 * DB 쪽에서 같은 사실을 지킨다. 여기는 **화면이 받는 모양**을 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTest {
  @Autowired MockMvc mvc;

  // ── 목록: 코스 추천 화면 (446:559) ────────────────────────────────────────

  /**
   * 칩마다 개수를 띄우고 0이면 비활성해야 한다. 4곳이 0인 것은 배 시간표를 아직 못 받아
   * 내도 코스를 뺐기 때문이다 — 빈 상태 화면이 필요한 근거가 이 숫자다.
   */
  @Test void 코스목록_칩별_개수를_내려준다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(3))
        .andExpect(jsonPath("$.counts['3']").value(1))
        .andExpect(jsonPath("$.counts['4']").value(0))
        .andExpect(jsonPath("$.counts['5']").value(2));
  }

  /** 카드 한 장을 그리는 데 필요한 것이 다 실려야 한다 — 썸네일·순서·9경 수·총 시간. */
  @Test void 코스목록_카드에_필요한_값이_실린다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[0].courseId").value(101))
        .andExpect(jsonPath("$.courses[0].courseCode").value("3-01"))
        .andExpect(jsonPath("$.courses[0].spotCount").value(3))
        .andExpect(jsonPath("$.courses[0].nineScenicCount").value(3))
        .andExpect(jsonPath("$.courses[0].departAt").value("11:05"))
        .andExpect(jsonPath("$.courses[0].returnAt").value("19:40"))
        .andExpect(jsonPath("$.courses[0].approxTotalText").value("약 8시간 30분"))
        // 카드의 원형 썸네일 3개 — poiId로 사진을 받고 shortName을 라벨로 쓴다
        .andExpect(jsonPath("$.courses[0].spots.length()").value(3))
        .andExpect(jsonPath("$.courses[0].spots[0].seq").value(1))
        .andExpect(jsonPath("$.courses[0].spots[0].poiId").value(4))
        .andExpect(jsonPath("$.courses[0].spots[0].shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.courses[0].spots[0].theme").value("BEACH"))
        // 지도 화면(446:717)이 핀을 찍는다
        .andExpect(jsonPath("$.courses[0].spots[0].lat").value(34.774752))
        .andExpect(jsonPath("$.courses[0].spots[2].shortName").value("바람의언덕"));
  }

  @Test void 코스목록_개수로_걸러진다() throws Exception {
    mvc.perform(get("/api/courses?spotCount=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(2))
        .andExpect(jsonPath("$.courses[0].courseCode").value("5-01"))
        .andExpect(jsonPath("$.courses[1].courseCode").value("5-07"));
  }

  /** 4곳은 아직 없다. 오류가 아니라 빈 목록이어야 한다 — 화면이 빈 상태를 그린다. */
  @Test void 코스목록_네곳은_빈_목록이다() throws Exception {
    mvc.perform(get("/api/courses?spotCount=4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(0))
        .andExpect(jsonPath("$.counts['4']").value(0));
  }

  /** §3 검증 코스 3종은 화면에 안 뜬다(추천 코스가 아니다). 목록에 섞이면 안 된다. */
  @Test void 코스목록에_검증코스_3종은_섞이지_않는다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains("3-01", "5-01", "5-07")))
        .andExpect(jsonPath("$.courses[*].courseId")
            .value(org.hamcrest.Matchers.everyItem(
                org.hamcrest.Matchers.greaterThan(3))));
  }

  // ── 상세: 코스 상세 화면 (446:929) ────────────────────────────────────────

  @Test void 코스상세_요약과_출처가_실린다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("3-01"))
        .andExpect(jsonPath("$.spotCount").value(3))
        .andExpect(jsonPath("$.approxTotalText").value("약 8시간 30분"))
        .andExpect(jsonPath("$.legCount").value(4))      // "· 4구간"
        .andExpect(jsonPath("$.originName").value("고현터미널"))
        .andExpect(jsonPath("$.service").value("WEEKDAY")) // 헤더 '평일' 칩
        .andExpect(jsonPath("$.baseDate").value("2026-08-18")) // 하단 출처
        .andExpect(jsonPath("$.source").value("거제시 BIS 원문"));
  }

  /**
   * ★ 타임라인의 심장. 구간마다 노선 번호가 실려야 한다 —
   * 이게 없으면 거제시 공식 앱의 "25분 / 13.0km"와 같은 층위가 된다(기준문서 §5).
   */
  @Test void 코스상세_구간마다_노선번호와_이동시간이_있다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.legs.length()").value(4))
        .andExpect(jsonPath("$.legs[0].fromName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[0].toName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.legs[0].mode").value("BUS"))
        .andExpect(jsonPath("$.legs[0].durationMin").value(40))
        .andExpect(jsonPath("$.legs[0].departAt").value("11:05"))
        .andExpect(jsonPath("$.legs[0].rides[0].routeNo").value("55"))
        .andExpect(jsonPath("$.legs[0].rides[0].boardStop").value("고현"))
        .andExpect(jsonPath("$.legs[1].durationMin").value(10)); // 학동→해금강
  }

  /**
   * ★★ 추정 시각을 확정과 갈라 말해야 한다(절대규칙 1).
   * leg.estimated 는 "이 구간 시각 중 하나라도 추정인가" — 화면이 배지 하나만 그리면 된다.
   */
  @Test void 코스상세_추정구간이_표시된다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.legs[0].estimated").value(false))
        .andExpect(jsonPath("$.legs[1].estimated").value(false))
        // 해금강 → 바람의언덕: 도장포 하차가 뒤 정류장 시각(상한)이라 추정이다
        .andExpect(jsonPath("$.legs[2].estimated").value(true))
        .andExpect(jsonPath("$.legs[2].rides[0].alightStop").value("도장포"))
        .andExpect(jsonPath("$.legs[2].rides[0].alightEstimated").value(true))
        .andExpect(jsonPath("$.legs[2].rides[0].boardEstimated").value(false))
        // 바람의언덕 → 고현: 도장포 승차가 앞 정류장 시각(하한)이라 추정이다
        .andExpect(jsonPath("$.legs[3].estimated").value(true))
        .andExpect(jsonPath("$.legs[3].rides[0].boardEstimated").value(true))
        .andExpect(jsonPath("$.estimatedLegCount").value(2));
  }

  /**
   * 거제조선해양문화관과 거제씨월드는 같은 '신촌' 정류장이라 버스를 타지 않는다.
   * 구간을 빼면 타임라인이 끊기고 화면이 '이유 없는 빈칸'을 그린다(절대규칙 3).
   */
  @Test void 코스상세_같은정류장_구간은_버스가_없다() throws Exception {
    mvc.perform(get("/api/courses/102"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legs.length()").value(6))
        .andExpect(jsonPath("$.legs[2].mode").value("SAME_STOP"))
        .andExpect(jsonPath("$.legs[2].fromName").value("조선해양문화관"))
        .andExpect(jsonPath("$.legs[2].toName").value("거제씨월드"))
        .andExpect(jsonPath("$.legs[2].durationMin").value(0))
        .andExpect(jsonPath("$.legs[2].rides.length()").value(0))
        .andExpect(jsonPath("$.legs[2].estimated").value(false));
  }

  /** 스팟을 누르면 시간표로 간다 — 어느 정류장에서 타는지가 응답에 있어야 한다. */
  @Test void 코스상세_스팟마다_체류와_타는곳이_있다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.stops.length()").value(3))
        .andExpect(jsonPath("$.stops[0].shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.stops[0].arriveAt").value("11:45"))
        .andExpect(jsonPath("$.stops[0].leaveAt").value("13:45"))
        .andExpect(jsonPath("$.stops[0].stayMin").value(120))
        .andExpect(jsonPath("$.stops[2].shortName").value("바람의언덕"));
  }

  /** 표시 시간에 "0분"이 붙으면 안 된다 — 595분은 "약 10시간"이다. */
  @Test void 코스상세_정시간일때는_분을_안_붙인다() throws Exception {
    mvc.perform(get("/api/courses/102"))
        .andExpect(jsonPath("$.approxTotalText").value("약 10시간"));
  }

  /** 검증 코스도 조회는 된다(saved_trips가 가리킬 수 있다). 단 구간이 없다. */
  @Test void 코스상세_검증코스는_구간이_없다() throws Exception {
    mvc.perform(get("/api/courses/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("부산발 당일치기"))
        .andExpect(jsonPath("$.courseCode").doesNotExist())
        .andExpect(jsonPath("$.legs.length()").value(0));
  }

  @Test void 코스상세_없는_코스는_404() throws Exception {
    mvc.perform(get("/api/courses/9999")).andExpect(status().isNotFound());
  }
}
