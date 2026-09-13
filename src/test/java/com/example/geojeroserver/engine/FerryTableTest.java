package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.engine.FerryTable.Coverage;
import com.example.geojeroserver.engine.FerryTable.Course;
import com.example.geojeroserver.engine.FerryTable.Sailing;
import com.example.geojeroserver.engine.FerryTable.Status;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 유람선 날짜 줄 — 순수 계산(DB·시계 없음, engine.md).
 *
 * 세 갈래를 가른다: PUBLISHED(원문이 공개한 날 — 0편이면 「예정된 배 없음」) /
 * UNPUBLISHED(첫 수집 이후인데 공개 범위 밖 — 「시각 미확인」) / NOT_COLLECTED(첫 수집 이전 — 「시각 미확인」).
 * 공개하지 않은 날을 「운행 없음」으로 말하면 §4에서 비판한 '이유 없는 빈칸'이다.
 */
class FerryTableTest {
  static final LocalDate D14 = LocalDate.of(2026, 9, 14);
  static final OffsetDateTime F1 = OffsetDateTime.parse("2026-09-14T01:04:06+09:00");
  static final OffsetDateTime F2 = OffsetDateTime.parse("2026-10-11T09:00:00+09:00");

  static final Course LANDING = new Course(1, true, 160);   // 도장포 외도상륙 약 2시간 40분
  static final Course CRUISE = new Course(2, false, 60);    // 도장포 선상관광 약 1시간
  static final Map<Long, Course> BOTH = Map.of(1L, LANDING, 2L, CRUISE);
  static final Map<Long, Course> LANDING_ONLY = Map.of(1L, LANDING);

  static final LocalDate SEP1 = LocalDate.of(2026, 9, 1);
  static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
  static final Coverage SEP = new Coverage(10, SEP1, D14, LocalDate.of(2026, 9, 30), F1, D14);
  static final Coverage OCT = new Coverage(11, OCT1, OCT1, LocalDate.of(2026, 10, 31), F1, D14);

  static int m(String hhmm) { return TimeUtil.hhmmToMin(hhmm); }

  static final List<Sailing> DOJANGPO_14_15 = List.of(
      new Sailing(10, 2, D14, m("14:00")),
      new Sailing(10, 1, D14, m("14:00")),
      new Sailing(10, 1, D14, m("10:30")),
      new Sailing(10, 1, D14.plusDays(1), m("10:30")),
      new Sailing(10, 1, D14.plusDays(1), m("14:00")));

  @Test void 공개된_날은_시각순이고_복귀는_출항_더하기_총_소요시간이다() {
    var r = FerryTable.build(List.of(SEP, OCT), DOJANGPO_14_15, BOTH, D14, 2, D14, null);
    assertEquals(2, r.rows().size());
    var day = r.rows().get(0);
    assertEquals(Status.PUBLISHED, day.status());
    assertEquals(List.of(m("10:30"), m("14:00"), m("14:00")), day.sailings().stream().map(FerryTable.Slot::departMin).toList());
    assertEquals(m("13:10"), day.sailings().get(0).returnMin());   // 10:30 + 160
  }

  @Test void 같은_시각이면_외도상륙_편이_먼저다() {
    var day = FerryTable.build(List.of(SEP), DOJANGPO_14_15, BOTH, D14, 1, D14, null).rows().get(0);
    assertEquals(1, day.sailings().get(1).courseId());
    assertEquals(2, day.sailings().get(2).courseId());
    assertEquals(m("15:00"), day.sailings().get(2).returnMin());   // 선상 14:00 + 60
  }

  @Test void 외도상륙만_보이면_선상_편이_빠지고_0편인_날도_공개된_날이다() {
    var onlyCruise = List.of(new Sailing(10, 2, D14, m("14:00")));
    var day = FerryTable.build(List.of(SEP), onlyCruise, LANDING_ONLY, D14, 1, D14, null).rows().get(0);
    assertEquals(Status.PUBLISHED, day.status());
    assertTrue(day.sailings().isEmpty());
  }

  @Test void 공개_범위_뒤는_시각_미확인이고_첫_수집_앞은_수집하지_않은_날이다() {
    var r = FerryTable.build(List.of(SEP, OCT), List.of(), BOTH, LocalDate.of(2026, 10, 30), 3, D14, null);
    assertEquals(List.of(Status.PUBLISHED, Status.PUBLISHED, Status.UNPUBLISHED),
        r.rows().stream().map(FerryTable.DayRow::status).toList());
    var before = FerryTable.build(List.of(SEP), List.of(), BOTH, LocalDate.of(2026, 9, 12), 2, D14, null);
    assertEquals(List.of(Status.NOT_COLLECTED, Status.NOT_COLLECTED),
        before.rows().stream().map(FerryTable.DayRow::status).toList());
  }

  @Test void 달_끝에_편이_없어_공개_범위가_짧으면_그_뒤는_운행_없음이_아니라_시각_미확인이다() {
    var shortSep = new Coverage(10, SEP1, D14, LocalDate.of(2026, 9, 28), F1, D14);
    var r = FerryTable.build(List.of(shortSep, OCT), List.of(), BOTH, LocalDate.of(2026, 9, 28), 4, D14, null);
    assertEquals(List.of(Status.PUBLISHED, Status.UNPUBLISHED, Status.UNPUBLISHED, Status.PUBLISHED),
        r.rows().stream().map(FerryTable.DayRow::status).toList());
  }

  @Test void 겹치는_수집이_있으면_늦게_수집한_것의_편만_쓴다() {
    var newer = new Coverage(20, OCT1, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 31), F2, LocalDate.of(2026, 10, 11));
    var d = LocalDate.of(2026, 10, 12);
    var sailings = List.of(new Sailing(11, 1, d, m("10:30")), new Sailing(20, 1, d, m("11:00")));
    var day = FerryTable.build(List.of(OCT, newer), sailings, BOTH, d, 1, D14, null).rows().get(0);
    assertEquals(List.of(m("11:00")), day.sailings().stream().map(FerryTable.Slot::departMin).toList());
  }

  /**
   * 2026-09-14 리뷰: 늦은 수집의 공개 범위가 옛 수집보다 좁으면(운항사가 달 끝 편을 내렸다), 좁아진 날에 옛 편이
   * PUBLISHED 로 되살아났다. 그 달을 늦게 수집한 이후의 날짜는 **늦은 수집만** 본다. 수집일보다 앞 날짜는 늦은 수집이
   * 볼 수 없으니(원문은 오늘부터 그린다) 옛 수집을 쓴다.
   */
  @Test void 늦은_수집의_공개_범위가_좁아지면_좁아진_날은_옛_편이_아니라_시각_미확인이다() {
    var newer = new Coverage(20, OCT1, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 28), F2, LocalDate.of(2026, 10, 11));
    var sailings = List.of(
        new Sailing(11, 1, LocalDate.of(2026, 10, 5), m("10:30")),
        new Sailing(11, 1, LocalDate.of(2026, 10, 30), m("10:30")),
        new Sailing(20, 1, LocalDate.of(2026, 10, 20), m("11:00")));
    var r = FerryTable.build(List.of(SEP, OCT, newer), sailings, BOTH, OCT1, 31, D14, null);
    java.util.function.Function<Integer, FerryTable.DayRow> day = d -> r.rows().get(d - 1);
    assertEquals(Status.PUBLISHED, day.apply(5).status());                  // 늦은 수집일(10/11) 전 — 옛 수집
    assertEquals(List.of(m("10:30")), day.apply(5).sailings().stream().map(FerryTable.Slot::departMin).toList());
    assertEquals(Status.UNPUBLISHED, day.apply(11).status());               // 늦은 수집일인데 첫 편(10/12) 전
    assertEquals(List.of(m("11:00")), day.apply(20).sailings().stream().map(FerryTable.Slot::departMin).toList());
    assertEquals(Status.UNPUBLISHED, day.apply(30).status());               // 옛 편이 되살아나면 안 된다
    assertTrue(day.apply(30).sailings().isEmpty());
  }

  @Test void 다음_배는_코스마다_지금_이후_첫_편이고_오늘_끝났으면_다음_날로_간다() {
    var r = FerryTable.build(List.of(SEP), DOJANGPO_14_15, BOTH, D14, 2, D14, m("12:30"));
    assertEquals(2, r.next().size());
    assertEquals(1, r.next().get(0).courseId());
    assertEquals(D14, r.next().get(0).date());
    assertEquals(m("14:00"), r.next().get(0).departMin());
    assertEquals(m("16:40"), r.next().get(0).returnMin());
    assertEquals(2, r.next().get(1).courseId());

    var late = FerryTable.build(List.of(SEP), DOJANGPO_14_15, LANDING_ONLY, D14, 2, D14, m("14:01"));
    assertEquals(D14.plusDays(1), late.next().get(0).date());
    assertEquals(m("10:30"), late.next().get(0).departMin());
  }

  @Test void 같은_시각이면_다음_배로_친다() {
    var r = FerryTable.build(List.of(SEP), DOJANGPO_14_15, LANDING_ONLY, D14, 1, D14, m("14:00"));
    assertEquals(m("14:00"), r.next().get(0).departMin());
  }

  @Test void 창_안에_남은_편이_없으면_그_코스의_다음_배는_없다() {
    var r = FerryTable.build(List.of(SEP), DOJANGPO_14_15, BOTH, D14, 2, D14, m("15:00"));
    assertEquals(List.of(1L), r.next().stream().map(FerryTable.Next::courseId).toList());   // 선상은 9/15에 편이 없다
  }

  @Test void 날짜를_하루_옮겨_물으면_어제_편은_다음_배가_아니다() {
    var r = FerryTable.build(List.of(SEP), DOJANGPO_14_15, LANDING_ONLY, D14, 2, D14.plusDays(1), null);
    assertEquals(D14.plusDays(1), r.next().get(0).date());
  }
}
