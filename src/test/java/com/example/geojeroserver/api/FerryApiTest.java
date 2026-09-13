package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 유람선 시간표 — `GET /api/pois/{id}/ferries` (2026-09-14 사용자 결정, Figma 프레임 없음).
 *
 * 값은 전부 V24 원문(외도유람선 예약센터 2026-09-14 01:04 KST 수집)에서 **정규식으로 따로 센** 앵커다.
 *   9/14 도장포 10:30(외도상륙) · 14:00(외도상륙) · 14:00(선상) / 와현 09:50 · 13:00 / 장승포 09:30 · 11:00 · 13:00 · 14:30 / 지세포 10:00 · 13:30
 *   총 소요시간(상품 페이지): 도장포 160·60 / 와현 180·80 / 장승포 180 / 지세포 210·90분
 * 다음 달 수집(새 V 번호)이 들어오면 겹치는 날짜는 늦은 수집이 이기므로 여기 앵커가 바뀔 수 있다 — 원문과 대조해 고친다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FerryApiTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private static final String Q = "date=2026-09-14&after=12:30";
  private static final String LANDING_DOJANGPO = "https://www.oedoticket.com/page/view.php?cid=bUlHNGo3WmV0dCttcDBNYVJSMUhZdz09";

  // ── 적재 ─────────────────────────────────────────────────────────────────

  @Test void 선착장_4곳_코스_7개_스팟_연결_6개가_들어왔다() {
    assertEquals(List.of("DOJANGPO", "WAHYEON", "JANGSEUNGPO", "JISEPO"),
        jdbc.queryForList("SELECT dock_code FROM ferry_docks ORDER BY seq", String.class));
    assertEquals(Map.of("DOJANGPO", "160,60", "WAHYEON", "180,80", "JANGSEUNGPO", "180", "JISEPO", "210,90"),
        Map.copyOf(jdbc.query("""
            SELECT d.dock_code, string_agg(c.total_min::text, ',' ORDER BY c.lands_on_oedo DESC) AS t
            FROM ferry_courses c JOIN ferry_docks d USING (dock_id) GROUP BY d.dock_code""",
            rs -> { var m = new java.util.HashMap<String, String>(); while (rs.next()) m.put(rs.getString(1), rs.getString(2)); return m; })));
    assertEquals(List.of("126581 DESTINATION", "126581 DESTINATION", "126581 DESTINATION", "126581 DESTINATION",
            "127182 DOCK", "129479 NEAR_DOCK"),
        jdbc.queryForList("""
            SELECT p.tour_content_id || ' ' || l.relation FROM ferry_links l JOIN pois p USING (poi_id)
            ORDER BY p.tour_content_id, l.relation""", String.class));
  }

  @Test void 편은_원문과_같은_1081개다() {
    assertEquals(1081, jdbc.queryForObject("SELECT count(*) FROM ferry_sailings", Integer.class));
  }

  // ── 외도보타니아: 선착장 4곳 · 외도상륙 편만 ─────────────────────────────

  @Test void 외도보타니아는_버스_정류장이_없고_선착장_4곳의_외도상륙_편만_준다() throws Exception {
    mvc.perform(get("/api/pois/5/ferries?" + Q))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poiId").value(5))
        .andExpect(jsonPath("$.shortName").value("외도보타니아"))
        .andExpect(jsonPath("$.hasBusStop").value(false))
        .andExpect(jsonPath("$.asOf.date").value("2026-09-14"))
        .andExpect(jsonPath("$.asOf.time").value("12:30"))
        .andExpect(jsonPath("$.days").value(14))
        .andExpect(jsonPath("$.ferries.length()").value(4))
        .andExpect(jsonPath("$.ferries[*].dock.dockCode", contains("DOJANGPO", "WAHYEON", "JANGSEUNGPO", "JISEPO")))
        .andExpect(jsonPath("$.ferries[*].relation", everyItem(is("DESTINATION"))))
        .andExpect(jsonPath("$.ferries[*].landingOnly", everyItem(is(true))))
        .andExpect(jsonPath("$.ferries[0].key").value("DESTINATION:DOJANGPO"))
        .andExpect(jsonPath("$.ferries[0].dock.shortName").value("도장포"))
        .andExpect(jsonPath("$.ferries[0].courses.length()").value(1))
        .andExpect(jsonPath("$.ferries[0].courses[0].landsOnOedo").value(true))
        .andExpect(jsonPath("$.ferries[0].courses[0].name").value("외도상륙+해금강, 십자동굴 선상관광(외도입장료 별도)"))
        .andExpect(jsonPath("$.ferries[0].courses[0].totalText").value("약 2시간 40분"))
        .andExpect(jsonPath("$.ferries[0].courses[0].oedoStayMin").value(120))
        .andExpect(jsonPath("$.ferries[0].courses[0].bookingUrl").value(LANDING_DOJANGPO))
        .andExpect(jsonPath("$.ferries[0].rows.length()").value(14))
        .andExpect(jsonPath("$.ferries[0].rows[0].date").value("2026-09-14"))
        .andExpect(jsonPath("$.ferries[0].rows[0].status").value("PUBLISHED"))
        .andExpect(jsonPath("$.ferries[0].rows[0].sailings[*].depart", contains("10:30", "14:00")))
        .andExpect(jsonPath("$.ferries[0].rows[0].sailings[0].returnApprox").value("13:10"))
        .andExpect(jsonPath("$.ferries[0].next.length()").value(1))
        .andExpect(jsonPath("$.ferries[0].next[0].date").value("2026-09-14"))
        .andExpect(jsonPath("$.ferries[0].next[0].depart").value("14:00"))
        .andExpect(jsonPath("$.ferries[0].next[0].returnApprox").value("16:40"))
        .andExpect(jsonPath("$.ferries[1].rows[0].sailings[*].depart", contains("09:50", "13:00")))
        .andExpect(jsonPath("$.ferries[1].next[0].returnApprox").value("16:00"))
        .andExpect(jsonPath("$.ferries[2].rows[0].sailings[*].depart", contains("09:30", "11:00", "13:00", "14:30")))
        .andExpect(jsonPath("$.ferries[3].rows[0].sailings[*].depart", contains("10:00", "13:30")))
        .andExpect(jsonPath("$.ferries[3].next[0].returnApprox").value("17:00"));
  }

  @Test void 공개_범위와_출처를_함께_준다() throws Exception {
    mvc.perform(get("/api/pois/5/ferries?" + Q))
        .andExpect(jsonPath("$.ferries[0].coverage.publishedThrough").value("2026-10-31"))
        .andExpect(jsonPath("$.ferries[0].coverage.fetchedAt").value("2026-09-14T01:04:06+09:00"))
        .andExpect(jsonPath("$.ferries[0].coverage.source").value("외도유람선 예약센터"))
        .andExpect(jsonPath("$.ferries[0].coverage.sourceUrl").value("https://oedoticket.com/page/time-schedule.php"))
        .andExpect(jsonPath("$.ferries[0].coverage.crossCheckUrl").value("https://www.dojangpo.kr/page/time-schedule.php"))
        .andExpect(jsonPath("$.ferries[1].coverage.crossCheckUrl").value(nullValue()));
  }

  // ── 도장포유람선: 스팟이 곧 선착장 · 두 코스 ─────────────────────────────

  @Test void 도장포유람선은_두_코스를_다_주고_다음_배도_코스마다_준다() throws Exception {
    mvc.perform(get("/api/pois/2/ferries?" + Q))
        .andExpect(jsonPath("$.hasBusStop").value(true))
        .andExpect(jsonPath("$.ferries.length()").value(1))
        .andExpect(jsonPath("$.ferries[0].relation").value("DOCK"))
        .andExpect(jsonPath("$.ferries[0].landingOnly").value(false))
        .andExpect(jsonPath("$.ferries[0].access").value(nullValue()))
        .andExpect(jsonPath("$.ferries[0].courses[*].landsOnOedo", contains(true, false)))
        .andExpect(jsonPath("$.ferries[0].courses[1].name").value("해금강, 십자동굴 선상관광(신선대,우제봉,외도상륙X)"))
        .andExpect(jsonPath("$.ferries[0].courses[1].totalText").value("약 1시간"))
        .andExpect(jsonPath("$.ferries[0].courses[1].oedoStayMin").value(nullValue()))
        .andExpect(jsonPath("$.ferries[0].rows[0].sailings[*].depart", contains("10:30", "14:00", "14:00")))
        .andExpect(jsonPath("$.ferries[0].rows[0].sailings[2].returnApprox").value("15:00"))
        .andExpect(jsonPath("$.ferries[0].next.length()").value(2));
  }

  // ── 다음 스팟이 외도일 때 ─────────────────────────────────────────────────

  @Test void 바람의언덕에서_외도로_가면_도장포_선착장_외도상륙_편과_원문_문장을_준다() throws Exception {
    mvc.perform(get("/api/pois/1/ferries?" + Q + "&toPoiId=5"))
        .andExpect(jsonPath("$.toPoiId").value(5))
        .andExpect(jsonPath("$.toIsFerryDestination").value(true))
        .andExpect(jsonPath("$.towardEmptyReason").value(nullValue()))
        .andExpect(jsonPath("$.ferries.length()").value(1))
        .andExpect(jsonPath("$.ferries[0].key").value("TOWARD:DOJANGPO"))
        .andExpect(jsonPath("$.ferries[0].relation").value("TOWARD"))
        .andExpect(jsonPath("$.ferries[0].landingOnly").value(true))
        .andExpect(jsonPath("$.ferries[0].access.quote").value("도보 1분거리에 바람의 언덕이 있습니다."))
        .andExpect(jsonPath("$.ferries[0].access.sourceUrl", containsString("cWQ0KzkzWlJ4eEZBbHBLMUtISVUxdz09")))
        .andExpect(jsonPath("$.ferries[0].courses.length()").value(1));
  }

  @Test void 근처_선착장이라는_원문이_없는_스팟에서_외도로_가면_비우고_이유를_준다() throws Exception {
    mvc.perform(get("/api/pois/3/ferries?" + Q + "&toPoiId=5"))
        .andExpect(jsonPath("$.toIsFerryDestination").value(true))
        .andExpect(jsonPath("$.ferries.length()").value(0))
        .andExpect(jsonPath("$.towardEmptyReason").value("NO_DOCK_NEAR_SPOT"));
  }

  @Test void 배와_무관한_스팟은_빈_목록이다() throws Exception {
    mvc.perform(get("/api/pois/4/ferries?" + Q))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasBusStop").value(true))
        .andExpect(jsonPath("$.toIsFerryDestination").value(false))
        .andExpect(jsonPath("$.ferries.length()").value(0));
  }

  // ── 세 갈래: 공개 / 시각 미확인 / 수집 전 ────────────────────────────────

  @Test void 공개_범위_뒤는_시각_미확인이다_운행_없음이_아니다() throws Exception {
    mvc.perform(get("/api/pois/5/ferries?date=2026-10-25"))
        .andExpect(jsonPath("$.ferries[0].rows[6].date").value("2026-10-31"))
        .andExpect(jsonPath("$.ferries[0].rows[6].status").value("PUBLISHED"))
        .andExpect(jsonPath("$.ferries[0].rows[7].date").value("2026-11-01"))
        .andExpect(jsonPath("$.ferries[0].rows[7].status").value("UNPUBLISHED"))
        .andExpect(jsonPath("$.ferries[0].rows[7].sailings.length()").value(0))
        .andExpect(jsonPath("$.ferries[0].rows[13].status").value("UNPUBLISHED"))
        .andExpect(jsonPath("$.ferries[0].coverage.publishedThrough").value("2026-10-31"));
  }

  @Test void 첫_수집_앞의_날짜는_수집하지_않은_날이다() throws Exception {
    mvc.perform(get("/api/pois/5/ferries?date=2026-09-01&days=3"))
        .andExpect(jsonPath("$.ferries[0].rows.length()").value(3))
        .andExpect(jsonPath("$.ferries[0].rows[*].status", everyItem(is("NOT_COLLECTED"))))
        .andExpect(jsonPath("$.ferries[0].next.length()").value(0));
  }

  /** 오늘이 아닌 날짜 — 내일을 묻는다(날짜를 박으면 그날이 오면 깨진다, 2026-09-14 리뷰). */
  @Test void 오늘이_아닌_날짜를_물으면_after가_없어도_그날_첫_편부터_다음_배다() throws Exception {
    var tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    var first = jdbc.queryForList("""
        SELECT to_char(min(s.depart_time), 'HH24:MI') FROM ferry_sailings s
        JOIN ferry_courses c USING (course_id) JOIN ferry_docks d ON d.dock_id = c.dock_id
        WHERE d.dock_code = 'DOJANGPO' AND c.lands_on_oedo AND s.sail_date = ?""", String.class, tomorrow);
    var res = mvc.perform(get("/api/pois/5/ferries?date=" + tomorrow))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOf.time").value(nullValue()));
    if (first.get(0) != null) {
      res.andExpect(jsonPath("$.ferries[0].next[0].date").value(tomorrow.toString()))
          .andExpect(jsonPath("$.ferries[0].next[0].depart").value(first.get(0)));
    }
  }

  @Test void 날짜를_주지_않으면_한국_시간_오늘이다() throws Exception {
    mvc.perform(get("/api/pois/5/ferries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOf.date").value(LocalDate.now(ZoneId.of("Asia/Seoul")).toString()))
        .andExpect(jsonPath("$.asOf.zone").value("Asia/Seoul"));
  }

  // ── 오류 ─────────────────────────────────────────────────────────────────

  @Test void 없는_스팟은_404다() throws Exception {
    mvc.perform(get("/api/pois/999/ferries?" + Q)).andExpect(status().isNotFound());
    mvc.perform(get("/api/pois/1/ferries?" + Q + "&toPoiId=999")).andExpect(status().isNotFound());
  }

  @Test void 형식이_틀린_날짜_시각_범위는_400이다() throws Exception {
    for (String q : new String[] {"date=2026-13-01", "date=2026-09-14&after=25:00", "date=2026-09-14&after=9:3",
        "date=2026-09-14&days=0", "date=2026-09-14&days=63", "date=2026-09-14&days=abc"}) {
      mvc.perform(get("/api/pois/5/ferries?" + q))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  // ── 옛 막배 ──────────────────────────────────────────────────────────────

  @Test void 도장포_막배_15시30분은_지웠다_날짜별_시간표가_대신한다() throws Exception {
    mvc.perform(get("/api/pois/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastDeparture").value(nullValue()));
  }
}
