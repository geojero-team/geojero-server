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
 * 값은 전부 V20 적재분(팀원 산출물 · 직행만 · 섬 코스 제외, BIS 원문 2026-08-18 평일)이고 CourseDataTest가
 * DB 쪽에서 같은 사실을 지킨다. 여기는 **화면이 받는 모양**을 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTest {
  @Autowired MockMvc mvc;

  // ── 목록: 코스 추천 화면 (446:559) ────────────────────────────────────────

  /**
   * 칩마다 개수를 띄우고 0이면 비활성해야 한다. 3·4곳은 순위 상위 10개, 5곳은 섬 코스를 뺀 뒤
   * 가능한 3개 전부다(V20). 배 시간표를 받아 섬 코스를 넣으면 이 숫자가 바뀐다.
   */
  @Test void 코스목록_칩별_개수를_내려준다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(23))
        .andExpect(jsonPath("$.counts['3']").value(10))
        .andExpect(jsonPath("$.counts['4']").value(10))
        .andExpect(jsonPath("$.counts['5']").value(3));
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
        // ★ 화면이 실제로 적는 것은 이쪽이다 — 구간 이동시간의 합(40+10+12+52=114분).
        // approxTotalMin(510분)에서 버스는 114분뿐이고 나머지는 머무는 시간이다(합 401분).
        // 얼마나 머무는지는 사용자가 정하므로 2026-09-13에 화면에서 뺐다 — 필드는 남긴다.
        .andExpect(jsonPath("$.courses[0].busMinTotal").value(114))
        .andExpect(jsonPath("$.courses[0].busTotalText").value("약 1시간 54분"))
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
        .andExpect(jsonPath("$.courses.length()").value(3))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains("5-01", "5-02", "5-03")));
  }

  /**
   * 4곳은 이제 10개다 — 전에는 섬(내도) 코스만 있어 0개였고 화면이 빈 상태를 그렸다.
   * 걸러도 counts 는 전량을 센다(칩이 다른 개수도 보여줘야 한다).
   */
  @Test void 코스목록_네곳은_10개이고_순위순이다() throws Exception {
    mvc.perform(get("/api/courses?spotCount=4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(10))
        .andExpect(jsonPath("$.courses[0].courseCode").value("4-01"))
        .andExpect(jsonPath("$.courses[9].courseCode").value("4-10"))
        .andExpect(jsonPath("$.counts['3']").value(10));
  }

  // ── 목록: 대표 코스 카드 v3 (Figma 582:416 · 585:417 · 585:485, 2026-09-14) ──────

  /**
   * ★ 대표 코스 10개 — 3/4/5곳 칩을 없애고 카드 10장만 보여준다(사용자 결정 2026-09-14).
   * 규칙: nine_scenic_count DESC, 버스 시간 합 ASC, spot_count ASC, course_code ASC.
   * 서버가 런타임에 고르므로 코스를 다시 적재하면 순서가 따라온다 — 기대값은 V20 적재분에 V28 이 9경 수를
   * 공식 표로 다시 센 값이다(매미성이 든 4-03 이 +1 되어 4-10 대신 10위).
   */
  @Test void 대표코스는_10개이고_9경_많고_버스_짧은_순이다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(10))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains(
                "3-01", "3-06", "3-02", "4-09", "3-04", "3-03", "5-01", "3-05", "4-02", "4-03")))
        // 칩은 없어졌지만 counts 는 그대로 준다 — 지도 화면 등 다른 호출과 응답 모양이 같아야 한다
        .andExpect(jsonPath("$.counts['3']").value(10));
  }

  /** featured 없이 부르면 전과 같다 — 23개 전량(코스 지도 화면이 쓴다). */
  @Test void featured_없이_부르면_23개_그대로다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses.length()").value(23))
        // 새 필드는 대표 코스가 아니어도 채운다 — 카드 한 장이 어느 목록에서 왔든 같은 모양이다
        .andExpect(jsonPath("$.courses[22].busRoutes").isArray())
        .andExpect(jsonPath("$.courses[22].nineScenicNos").isArray());
  }

  /**
   * 카드 한 장 — 제목 · 9경 번호 · 노선 · 하루 회차 · 요일. 3-01 은 55번 한 노선이라 회차가 붙고,
   * 55번은 매일 같아(§2 「50번대 전체 매일 동일」) 평일·휴일 둘 다 6이다(BIS 표지 「1일 6회」).
   */
  @Test void 대표코스_카드에_제목_9경번호_노선_배차_요일이_실린다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[0].courseCode").value("3-01"))
        .andExpect(jsonPath("$.courses[0].title").value("환승 없이 남부 9경 세 곳"))
        .andExpect(jsonPath("$.courses[0].intro").isString())
        // 학동(4경) · 해금강(1경) · 바람의언덕(2경) — 방문 순서가 아니라 번호 오름차순
        .andExpect(jsonPath("$.courses[0].nineScenicNos")
            .value(org.hamcrest.Matchers.contains(1, 2, 4)))
        .andExpect(jsonPath("$.courses[0].busRoutes")
            .value(org.hamcrest.Matchers.contains("55")))
        .andExpect(jsonPath("$.courses[0].tripsPerDay.weekday").value(6))
        .andExpect(jsonPath("$.courses[0].tripsPerDay.holiday").value(6))
        .andExpect(jsonPath("$.courses[0].holidayService").value(true));
  }

  /**
   * 노선이 여럿이면 회차 수를 말하지 않는다 — 어느 노선의 횟수인지 말할 수 없고, 더하면 실제로 타지
   * 않는 숫자가 된다. 3-02 의 22-1 은 20번대라 평일/휴일이 갈라져(§2) 휴일에 그 회차가 없다 → holidayService false.
   */
  @Test void 노선이_여럿이면_배차횟수가_없고_휴일운행이_아니다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[2].courseCode").value("3-02"))
        // 구간·승차 순서대로, 중복 없이(55번은 두 번 타지만 한 번만)
        .andExpect(jsonPath("$.courses[2].busRoutes")
            .value(org.hamcrest.Matchers.contains("22-1", "67-1", "55")))
        .andExpect(jsonPath("$.courses[2].tripsPerDay").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[2].holidayService").value(false));
  }

  /**
   * 휴일 운행은 노선이 아니라 **회차**로 본다 — 코스의 모든 승차가 휴일 시간표에 같은 시각으로 있어야 참이다.
   * 운영 기대값(브리프 courses.json): 55·55-1·67-1·33·61 만 타는 여섯 코스가 참이고, 10·11·22·22-1·60·32·32-1
   * 이 섞인 나머지는 거짓이다(10·20번대는 평일/휴일 분리 — §2 요일 구조).
   */
  @Test void 휴일에도_타는_코스는_여섯이다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[?(@.holidayService == true)].courseCode")
            .value(org.hamcrest.Matchers.containsInAnyOrder(
                "3-01", "3-04", "3-05", "3-06", "4-01", "4-03")));
  }

  /**
   * 9경 번호는 pois(V28, 기준문서 §6 표)에서 온다. 팀원 적재값(V20 nine_scenic_count)은 매미성(9경 9번)을 세지
   * 않아 4-07 이 0 이었는데, V28 이 공식 번호로 다시 세어 1 이다 — 배지 「거제 9경 · 9경」과 수가 맞는다.
   * 9경이 없는 코스는 빈 목록이지 null 이 아니다 — 23개 중에는 그런 코스가 없다.
   */
  @Test void 구경_번호는_공식_표를_따르고_적재된_9경_수와_맞는다() throws Exception {
    mvc.perform(get("/api/courses?spotCount=4"))
        .andExpect(jsonPath("$.courses[6].courseCode").value("4-07"))
        .andExpect(jsonPath("$.courses[6].nineScenicCount").value(1))
        .andExpect(jsonPath("$.courses[6].nineScenicNos")
            .value(org.hamcrest.Matchers.contains(9)))
        // 제목·소개는 대표 10개에만 있다 — 없는 코스는 null 로 온다(필드가 빠지지 않는다)
        .andExpect(jsonPath("$.courses[6].title").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[6].intro").value(org.hamcrest.Matchers.nullValue()));
  }

  /** §3 검증 코스 3종은 화면에 안 뜬다(추천 코스가 아니다). 목록에 섞이면 안 된다. */
  @Test void 코스목록에_검증코스_3종은_섞이지_않는다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains(
                "3-01", "3-02", "3-03", "3-04", "3-05", "3-06", "3-07", "3-08", "3-09", "3-10",
                "4-01", "4-02", "4-03", "4-04", "4-05", "4-06", "4-07", "4-08", "4-09", "4-10",
                "5-01", "5-02", "5-03")))
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
        .andExpect(jsonPath("$.busMinTotal").value(114))     // legs 합과 같아야 한다
        .andExpect(jsonPath("$.busTotalText").value("약 1시간 54분"))
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

  /** 코스 상세 제목은 title 이 있으면 그것을 쓴다(없으면 화면이 「{첫 스팟}에서 {끝 스팟}까지」로 폴백). */
  @Test void 코스상세에_제목과_소개가_실린다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.title").value("환승 없이 남부 9경 세 곳"))
        .andExpect(jsonPath("$.intro").isString());
    // 대표 10개 밖의 코스는 null — 필드가 빠지지 않는다
    mvc.perform(get("/api/courses/119"))
        .andExpect(jsonPath("$.courseCode").value("4-07"))
        .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.nullValue()));
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
