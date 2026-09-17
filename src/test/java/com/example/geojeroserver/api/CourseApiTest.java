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
   * 가능한 3개 전부다(V20). V36 이 3-11 을, V37 이 2차 세트 새 코스 6개(3곳 둘 · 4곳 둘 · 5곳 하나 · 6곳 하나)를 더했다.
   * counts 는 지금 계약 그대로 3·4·5 세 칸이다 — 6곳 코스(6-01)는 칸이 없다.
   */
  @Test void 코스목록_칩별_개수를_내려준다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(30))
        .andExpect(jsonPath("$.counts['3']").value(13))
        .andExpect(jsonPath("$.counts['4']").value(12))
        .andExpect(jsonPath("$.counts['5']").value(4))
        .andExpect(jsonPath("$.counts['6']").doesNotExist());
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
        .andExpect(jsonPath("$.courses.length()").value(4))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains("5-01", "5-02", "5-03", "5-04")));
  }

  /**
   * 4곳은 12개다 — V20 의 10개에 V37 의 4-11 · 4-12. 전에는 섬(내도) 코스만 있어 0개였고 화면이 빈 상태를 그렸다.
   * 걸러도 counts 는 전량을 센다(칩이 다른 개수도 보여줘야 한다).
   */
  @Test void 코스목록_네곳은_12개이고_순위순이다() throws Exception {
    mvc.perform(get("/api/courses?spotCount=4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(12))
        .andExpect(jsonPath("$.courses[0].courseCode").value("4-01"))
        .andExpect(jsonPath("$.courses[9].courseCode").value("4-10"))
        .andExpect(jsonPath("$.courses[11].courseCode").value("4-12"))
        .andExpect(jsonPath("$.counts['3']").value(13));
  }

  // ── 목록: 대표 코스 카드 v3 (Figma 582:416 · 585:417 · 585:485, 2026-09-14) ──────

  /**
   * ★ 대표 코스 — 코스 재설계 2차 세트 일곱(V37, 코스재설계 §5-1). **featured_rank 순서 그대로** 준다.
   * 옛 규칙(9경 많은 순 · 버스 짧은 순)은 버렸다 — 카드 10장의 배지가 전부 「거제 9경 · N경」이라 코스마다 무엇이
   * 다른지 화면이 말하지 않았다(사용자 지적). 이제 사람이 성격 축 셋으로 골라 순서를 박는다.
   */
  @Test void 대표코스는_일곱이고_featured_rank_순이다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses.length()").value(7))
        .andExpect(jsonPath("$.courses[*].courseCode")
            .value(org.hamcrest.Matchers.contains(
                "4-11", "6-01", "3-12", "3-13", "3-11", "4-12", "5-04")))
        .andExpect(jsonPath("$.courses[*].featuredRank")
            .value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6, 7)))
        // 칩은 클라가 대표 목록 안에서 센다. counts 는 그대로 준다 — 다른 호출과 응답 모양이 같아야 한다
        .andExpect(jsonPath("$.counts['3']").value(13));
  }

  /**
   * 카드 첫 줄 배지가 무엇인지 — 이 코스를 어느 성격 축으로 골랐나(badgeAxis). 우리 큐레이션 선택이라 저장이 정직하다.
   * 분류 구성은 spots[].theme 로, 9경은 nineScenicNos 로, 배는 ferryMinTotal 로 클라가 센다(코스재설계 §5-3).
   */
  @Test void 대표코스마다_성격축이_실린다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[*].badgeAxis")
            .value(org.hamcrest.Matchers.contains(
                "OFFICIAL", "OFFICIAL", "THEME", "THEME", "THEME", "NINE", "NINE")));
  }

  /**
   * 카드 제목은 **무엇을 보는가**다 — 성격 축(거제시가 묶은 곳 · 9경)은 사진 위 배지가 말하므로 제목에서 뺐다(2026-09-17).
   * ③ ④ ⑤ 는 처음부터 그 모양이라 그대로고, ① ② ⑥ 을 다시 썼다. 근거는 TourAPI 소개문이다(V37 주석).
   * ② 의 기념탑은 「옥포대첩」 기념탑이다 — 소개문은 「옥포승첩을 기념하고 … 기념탑」이고 이순신 장군은 참배단의 영정이라,
   * 「이순신 기념탑」은 원천에 없는 말이다(검토에서 고침).
   */
  @Test void 대표코스_제목은_무엇을_보는가를_말한다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[*].title")
            .value(org.hamcrest.Matchers.contains(
                "몽돌 소리와 풍차 언덕, 배 만드는 원리",
                "돌고래와 옥포대첩 기념탑, 굵은 대나무 숲",
                "풍차 언덕과 바위섬, 돌로 쌓은 성",
                "유리 돔 식물원에서 매미성 지나 맹종죽 숲까지",
                "배로 건너가는 거제 9경, 외도보타니아",
                "포로수용소 유적에서 바다의 금강산까지",
                "매미성에서 옛 관아 지나 포로수용소 유적까지")));
  }

  /**
   * 거제시 공식 코스 — 원문(tour.geoje.go.kr 관광코스)이 묶은 장소 수 · 이 코스와 겹친 곳 수 · 원문 순서 그대로인가.
   * 당일코스는 여섯 곳 중 네 곳(학동 · 바람의언덕 · 해금강 · 조선해양문화관, 원문 순서와 같다),
   * 2일코스는 열여섯 곳 중 여섯 곳(원문 1일차의 학동 뒤로 2일차의 조선해양문화관 · 씨월드 · 양지암 · 옥포 · 맹종죽 — 순서가 같다).
   */
  @Test void 공식코스_축의_카드에는_거제시_원문_대조가_실린다() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[0].officialCourse.name").value("당일코스"))
        .andExpect(jsonPath("$.courses[0].officialCourse.total").value(6))
        .andExpect(jsonPath("$.courses[0].officialCourse.matched").value(4))
        .andExpect(jsonPath("$.courses[0].officialCourse.orderKept").value(true))
        .andExpect(jsonPath("$.courses[0].officialCourse.sourceUrl")
            .value("https://tour.geoje.go.kr/index.geoje?menuCd=DOM_000008502008002000"))
        .andExpect(jsonPath("$.courses[1].officialCourse.name").value("2일코스"))
        .andExpect(jsonPath("$.courses[1].officialCourse.total").value(16))
        .andExpect(jsonPath("$.courses[1].officialCourse.matched").value(6))
        .andExpect(jsonPath("$.courses[1].officialCourse.orderKept").value(true))
        .andExpect(jsonPath("$.courses[1].officialCourse.sourceUrl")
            .value("https://tour.geoje.go.kr/index.geoje?menuCd=DOM_000008502008002000"))
        // 다른 축은 공식 코스가 없다 — 필드는 null 로 온다
        .andExpect(jsonPath("$.courses[2].officialCourse").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[6].officialCourse").value(org.hamcrest.Matchers.nullValue()));
  }

  /** 대표가 아닌 코스는 순서 · 성격 축 · 공식 코스가 전부 null 이다 — 같은 카드 모양에 값만 빈다. */
  @Test void 대표가_아닌_코스는_순서와_성격축이_없다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[0].courseCode").value("3-01"))
        .andExpect(jsonPath("$.courses[0].featuredRank").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[0].badgeAxis").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[0].officialCourse").value(org.hamcrest.Matchers.nullValue()))
        // featured 없이 불러도 대표 코스에는 값이 실린다 — 카드 한 장이 어느 목록에서 왔든 같은 말을 한다
        .andExpect(jsonPath("$.courses[?(@.courseCode == '4-11')].featuredRank").value(1))
        .andExpect(jsonPath("$.courses[?(@.courseCode == '4-11')].badgeAxis").value("OFFICIAL"));
  }

  /** featured 없이 부르면 전량이다 — 30개(코스 지도 화면이 쓴다). */
  @Test void featured_없이_부르면_전량이다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses.length()").value(30))
        // 새 필드는 대표 코스가 아니어도 채운다 — 카드 한 장이 어느 목록에서 왔든 같은 모양이다
        .andExpect(jsonPath("$.courses[22].busRoutes").isArray())
        .andExpect(jsonPath("$.courses[22].nineScenicNos").isArray());
  }

  /**
   * 카드 한 장 — 제목 · 9경 번호 · 노선 · 하루 회차 · 요일. 3-01 은 55번 한 노선이라 회차가 붙고,
   * 55번은 매일 같아(§2 「50번대 전체 매일 동일」) 평일·휴일 둘 다 6이다(BIS 표지 「1일 6회」).
   */
  @Test void 대표코스_카드에_제목_9경번호_노선_배차_요일이_실린다() throws Exception {
    mvc.perform(get("/api/courses"))
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
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[1].courseCode").value("3-02"))
        // 구간·승차 순서대로, 중복 없이(55번은 두 번 타지만 한 번만)
        .andExpect(jsonPath("$.courses[1].busRoutes")
            .value(org.hamcrest.Matchers.contains("22-1", "67-1", "55")))
        .andExpect(jsonPath("$.courses[1].tripsPerDay").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.courses[1].holidayService").value(false));
  }

  /**
   * 휴일 운행은 노선이 아니라 **회차**로 본다 — 코스의 모든 승차가 휴일 시간표에 같은 시각으로 있어야 참이다.
   * 운영 기대값(브리프 courses.json): 55·55-1·67-1·33·61 만 타는 여섯 코스가 참이고, 10·11·22·22-1·60·32·32-1
   * 이 섞인 나머지는 거짓이다(10·20번대는 평일/휴일 분리 — §2 요일 구조).
   * 3-11(배 코스, V36)도 참이다 — 55번 한 노선이고 55번은 평일·휴일 시각이 같다.
   * ⚠️ 배는 여기서 안 본다. holidayService 는 **승차(course_rides)**로만 판정하는데 배는 승차가 없다.
   * 유람선은 요일이 아니라 날짜마다 운항이 다르므로(부록 G) 그 판단은 화면의 배 시간표가 한다.
   *
   * V37 새 코스 여섯 중 셋(6-01 · 3-12 · 3-13)은 모든 편을 **평일·휴일 시간표에 다 있는 편**으로 골라 참이다.
   * 나머지 셋(4-11 · 4-12 · 5-04)은 그런 편으로 사슬이 안 이어져 평일 편으로 실었다 — 거짓이다.
   * 4-11 은 고현터미널 → 지세포 22번(20번대 평일/휴일 분리), 4-12 · 5-04 는 포로수용소 100 · 110번 편이 평일에만 있다
   * (4-12 는 포로수용소를 맨 앞에 둬도 그렇다 — 포로수용소 → 고현터미널 평일 · 휴일 공통 편이 08:25 뒤로 14:15 까지 없다).
   */
  @Test void 휴일에도_타는_코스는_열이다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[?(@.holidayService == true)].courseCode")
            .value(org.hamcrest.Matchers.containsInAnyOrder(
                "3-01", "3-04", "3-05", "3-06", "4-01", "4-03", "3-11", "6-01", "3-12", "3-13")));
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
                "3-11", "3-12", "3-13",
                "4-01", "4-02", "4-03", "4-04", "4-05", "4-06", "4-07", "4-08", "4-09", "4-10",
                "4-11", "4-12",
                "5-01", "5-02", "5-03", "5-04",
                "6-01")))
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
  @Test void 코스상세_구간마다_내리는_정류장과_직선거리가_있다() throws Exception {
    // 버스가 내려주는 곳은 스팟이 아니라 정류장이다 — 「55번 · 10분」만 적으면 「10분 뒤 스팟 도착」으로 읽힌다.
    // 해금강은 내리는 정류장에서 직선 1km 가 넘는다(2026-09-16 사용자 결정 — 디자인브리프 부록 H).
    mvc.perform(get("/api/courses/101"))
        // 271m 은 **내릴 때** 정류장에서 잰 값이다 — 타는 곳 카드의 311m(반대 방향 정류장)과 다르다.
        .andExpect(jsonPath("$.legs[0].alight.stop").value("학동"))
        .andExpect(jsonPath("$.legs[0].alight.distanceM").value(271))
        .andExpect(jsonPath("$.legs[1].alight.stop").value("해금강종점"))
        .andExpect(jsonPath("$.legs[1].alight.distanceM").value(1070))
        .andExpect(jsonPath("$.legs[2].alight.stop").value("도장포"))
        // 마지막 구간은 고현터미널로 돌아간다 — 내려서 갈 스팟이 없다
        .andExpect(jsonPath("$.legs[3].toName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[3].alight").doesNotExist());
  }

  @Test void 코스상세_내리는_정류장은_그_구간의_노선을_따른다() throws Exception {
    // 같은 스팟이라도 노선마다 서는 정류장이 다르다 — 학동몽돌해변은 55번이 「학동」(271m), 67-1번이 「학동삼거리」(106m).
    mvc.perform(get("/api/courses/121"))
        .andExpect(jsonPath("$.legs[2].rides[0].routeNo").value("67-1"))
        .andExpect(jsonPath("$.legs[2].toName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.legs[2].alight.stop").value("학동삼거리"))
        .andExpect(jsonPath("$.legs[2].alight.distanceM").value(106));
  }

  @Test void 코스상세_마지막_구간에는_돌아갈_때_타는_정류장이_있다() throws Exception {
    // 마지막 구간은 고현터미널로 돌아가는 길이라 내릴 스팟이 없다. 대신 **어디서 타는지**를 말한다 —
    // 없으면 마지막 스팟에서 버스를 어디서 기다릴지 화면이 한 글자도 말하지 않는다(2026-09-16 사용자 지적).
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.legs[3].toName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[3].alight").doesNotExist())
        .andExpect(jsonPath("$.legs[3].board.stop").value("도장포"))
        .andExpect(jsonPath("$.legs[3].board.distanceM").value(376));
  }

  @Test void 코스상세_돌아갈_때_타는_곳이_내린_곳과_다를_수_있다() throws Exception {
    // 학동몽돌해변으로 끝나는 코스 셋(3-04 · 4-03 · 4-04)은 67-1 로 「학동삼거리」(106m)에 내리고
    // 돌아갈 때는 55번을 「학동」(311m)에서 탄다 — 다른 정류장이고 3배 멀다.
    mvc.perform(get("/api/courses/115"))
        .andExpect(jsonPath("$.legs[3].alight.stop").value("학동삼거리"))
        .andExpect(jsonPath("$.legs[4].toName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[4].board.stop").value("학동"))
        .andExpect(jsonPath("$.legs[4].board.distanceM").value(311));
  }

  @Test void 코스상세_고현터미널에서_떠나는_첫_구간에는_타는_곳이_없다() throws Exception {
    // 터미널이 곧 정류장이라 0m 다 — 「고현터미널에서 타요 · 직선 약 0m」는 말이 안 된다.
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.legs[0].fromName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[0].board").doesNotExist())
        .andExpect(jsonPath("$.legs[0].alight.stop").value("학동"));
  }

  @Test void 코스상세_씨월드는_시각은_지세포지만_내리는_곳은_신촌이다() throws Exception {
    // 「내리는 곳과 시간표를 읽는 곳이 다르다」는 규칙(부록 G 5-1 · 부록 J)을 코스 화면도 같은 말로 한다.
    mvc.perform(get("/api/courses/121"))
        .andExpect(jsonPath("$.legs[0].toName").value("조선해양문화관"))
        .andExpect(jsonPath("$.legs[0].rides[0].alightStop").value("지세포"))
        .andExpect(jsonPath("$.legs[0].alight.stop").value("신촌"))
        .andExpect(jsonPath("$.legs[0].alight.distanceM").value(184));
  }

  @Test void 코스상세_같은정류장_구간에는_내리는_곳이_없다() throws Exception {
    // 버스를 타지 않으므로 내리지도 않는다(조선해양문화관 → 거제씨월드).
    mvc.perform(get("/api/courses/121"))
        .andExpect(jsonPath("$.legs[1].mode").value("SAME_STOP"))
        .andExpect(jsonPath("$.legs[1].alight").doesNotExist());
  }

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

  // ── 되짚기 (V37) ─────────────────────────────────────────────────────────
  //
  // 두 스팟 사이에 직행이 없으면 고현터미널을 한 번 거친다(코스당 한 번 — 코스재설계 §3).
  // 구간 둘로 담는다: 「A → 고현터미널」 + 「고현터미널 → B」. 구간마다 버스 한 대라 환승 없음 규칙이 그대로다.
  // 그래서 코스 상세 타임라인 **가운데에 고현터미널 줄**이 생긴다 — 클라가 이 두 구간의 null 과 이름으로 그린다.

  /** 4-11: 학동 · 바람의언덕 · 해금강 → (고현터미널) → 조선해양문화관. 해금강에서 지세포로 가는 직행이 없다. */
  @Test void 코스상세_되짚기_구간은_고현터미널로_갔다가_고현터미널에서_떠난다() throws Exception {
    mvc.perform(get("/api/courses/125"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("4-11"))
        .andExpect(jsonPath("$.legs.length()").value(6))
        .andExpect(jsonPath("$.legs[3].fromName").value("해금강"))
        .andExpect(jsonPath("$.legs[3].toPoiId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.legs[3].toName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[3].mode").value("BUS"))
        .andExpect(jsonPath("$.legs[4].fromPoiId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.legs[4].fromName").value("고현터미널"))
        .andExpect(jsonPath("$.legs[4].toName").value("조선해양문화관"))
        .andExpect(jsonPath("$.legs[4].mode").value("BUS"))
        // 스팟은 넷이다 — 고현터미널은 스팟 줄이 아니다
        .andExpect(jsonPath("$.stops.length()").value(4));
  }

  /**
   * 되짚기 구간의 정류장 줄 — 고현터미널로 가는 구간에는 내리는 곳이 없고(터미널이 곧 정류장이다),
   * 고현터미널에서 떠나는 구간에는 타는 곳이 없다. 첫 구간 · 마지막 구간과 같은 규칙이다.
   */
  @Test void 코스상세_되짚기_구간의_터미널쪽에는_정류장_줄이_없다() throws Exception {
    mvc.perform(get("/api/courses/125"))
        .andExpect(jsonPath("$.legs[3].alight").doesNotExist())
        .andExpect(jsonPath("$.legs[4].board").doesNotExist());
  }

  /** 새 코스 여섯(+ 3-11)의 휴일 운행 — 카드가 「평일·휴일」이라고 말할 수 있는 코스만 참이다. */
  @Test void 대표코스의_휴일운행_기대값() throws Exception {
    mvc.perform(get("/api/courses?featured=true"))
        .andExpect(jsonPath("$.courses[*].holidayService")
            .value(org.hamcrest.Matchers.contains(false, true, true, true, true, false, false)));
  }

  // ── 배 구간 (V35) ────────────────────────────────────────────────────────
  //
  // 외도보타니아는 거제 9경 3경인데 **배로만 간다**. 시간표(V23·V24, 1,081편)는 진작 있었는데
  // course_legs.mode 가 BUS·SAME_STOP 뿐이라 코스에 담을 자리가 없었다.
  // 배는 버스와 두 가지가 다르다 — ① 떠난 선착장으로 **돌아온다** ② 시각이 **날짜마다 다르다**.

  /** 배 코스가 목록에 선다. 스팟 셋(바람의언덕 · 도장포유람선 · 외도보타니아) 중 9경이 둘이다. */
  @Test void 코스목록에_배_코스가_있다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses[?(@.courseCode == '3-11')].spotCount").value(3))
        .andExpect(jsonPath("$.courses[?(@.courseCode == '3-11')].nineScenicCount").value(2));
  }

  /**
   * 배는 **왕복 한 덩어리**라 구간이 둘이다 — 가는 구간(외도까지)과 돌아오는 구간(선착장으로).
   * 원문이 주는 것은 왕복 + 외도 체류를 합친 총 소요시간(약 2시간 40분) 하나뿐이라
   * 그 값을 **가는 구간에 싣고 돌아오는 구간은 0분**이다. 한 방향 시간을 우리가 쪼개 만들지 않는다.
   */
  @Test void 코스상세_배_구간은_가는_구간과_돌아오는_구간_둘이다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legs.length()").value(5))
        .andExpect(jsonPath("$.legs[1].mode").value("FERRY"))
        .andExpect(jsonPath("$.legs[1].fromName").value("도장포유람선"))
        .andExpect(jsonPath("$.legs[1].toName").value("외도보타니아"))
        .andExpect(jsonPath("$.legs[1].durationMin").value(160))
        .andExpect(jsonPath("$.legs[2].mode").value("FERRY"))
        .andExpect(jsonPath("$.legs[2].fromName").value("외도보타니아"))
        .andExpect(jsonPath("$.legs[2].toName").value("도장포유람선"))
        .andExpect(jsonPath("$.legs[2].durationMin").value(0));
  }

  /**
   * 화면이 「외도상륙 유람선 · 약 2시간 40분」 + 「외도에 2시간 머물러요 · 입장료 별도」를 그리려면
   * 이 넷이 필요하다. 코스 이름은 **원문 그대로**여야 한다 — 「외도입장료 별도」가 거기 있다.
   */
  @Test void 코스상세_배_구간에_유람선_정보가_실린다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(jsonPath("$.legs[1].ferry.legendLabel").value("외도상륙+해금강선상관광"))
        .andExpect(jsonPath("$.legs[1].ferry.courseName")
            .value(org.hamcrest.Matchers.containsString("외도입장료 별도")))
        .andExpect(jsonPath("$.legs[1].ferry.totalText").value("약 2시간 40분"))
        .andExpect(jsonPath("$.legs[1].ferry.stayMin").value(120))
        .andExpect(jsonPath("$.legs[1].ferry.dockName").value("도장포"))
        .andExpect(jsonPath("$.legs[1].ferry.landsOnOedo").value(true))
        .andExpect(jsonPath("$.legs[1].ferry.bookingUrl")
            .value(org.hamcrest.Matchers.startsWith("https://")));
  }

  /** 돌아오는 구간에도 같은 배 정보가 실린다 — 화면이 「도장포 선착장으로 돌아와요」를 그린다. */
  @Test void 코스상세_돌아오는_배_구간에도_선착장이_실린다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(jsonPath("$.legs[2].ferry.dockName").value("도장포"))
        .andExpect(jsonPath("$.legs[2].ferry.totalText").value("약 2시간 40분"));
  }

  /**
   * ★ 배 분을 버스로 세면 카드의 「버스 약 N분」이 거짓말이 된다.
   * 버스 102분(50 + 52) 과 배 160분을 갈라 센다. 같은 정류장 구간은 0분이라 양쪽 다 영향이 없다.
   */
  @Test void 코스상세_버스_시간과_배_시간을_갈라_센다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(jsonPath("$.busMinTotal").value(102))
        .andExpect(jsonPath("$.busTotalText").value("약 1시간 42분"))
        .andExpect(jsonPath("$.ferryMinTotal").value(160))
        .andExpect(jsonPath("$.ferryTotalText").value("약 2시간 40분"));
  }

  /** 코스 목록 카드도 같이 갈라 받는다 — 카드에 두 값을 나란히 적는다. */
  @Test void 코스목록_카드도_배_시간을_따로_받는다() throws Exception {
    mvc.perform(get("/api/courses"))
        .andExpect(jsonPath("$.courses[?(@.courseCode == '3-11')].busMinTotal").value(102))
        .andExpect(jsonPath("$.courses[?(@.courseCode == '3-11')].ferryMinTotal").value(160));
  }

  /**
   * 배 시각은 **날짜마다 다르다**(도장포 외도상륙 편은 10:30 이 38일 · 14:00 이 36일이고 나머지는 제각각).
   * 그래서 구간에 시각을 박지 않는다 — 박으면 그 날짜에만 맞는 말이 된다.
   * 버스 구간은 지금처럼 회차를 골라 박는다(48일 전부 12시 이후 편이 있어 오후 배를 늘 탈 수 있다).
   */
  @Test void 코스상세_배_구간에는_시각을_박지_않는다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(jsonPath("$.legs[1].departAt").doesNotExist())
        .andExpect(jsonPath("$.legs[1].arriveAt").doesNotExist())
        .andExpect(jsonPath("$.legs[0].departAt").value("11:05"))
        .andExpect(jsonPath("$.departAt").value("11:05"))
        .andExpect(jsonPath("$.returnAt").value("19:40"));
  }

  /** 버스 구간에는 배 정보가 없다 — 없는 칸을 만들지 않는다. */
  @Test void 코스상세_버스_구간에는_배_정보가_없다() throws Exception {
    mvc.perform(get("/api/courses/124"))
        .andExpect(jsonPath("$.legs[0].mode").value("BUS"))
        .andExpect(jsonPath("$.legs[0].ferry").doesNotExist())
        .andExpect(jsonPath("$.legs[3].mode").value("SAME_STOP"))
        .andExpect(jsonPath("$.legs[3].ferry").doesNotExist())
        .andExpect(jsonPath("$.legs[4].mode").value("BUS"))
        .andExpect(jsonPath("$.legs[4].ferry").doesNotExist());
  }

  /** 배가 없는 코스는 배 시간이 0이고, 화면은 그 칩을 안 그린다. */
  @Test void 코스상세_배가_없는_코스는_배_시간이_0이다() throws Exception {
    mvc.perform(get("/api/courses/101"))
        .andExpect(jsonPath("$.ferryMinTotal").value(0))
        .andExpect(jsonPath("$.ferryTotalText").doesNotExist());
  }
}
