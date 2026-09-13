package com.example.geojeroserver.engine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 유람선 날짜 줄 — 순수 계산. DB·HTTP·시계를 모른다(engine.md). 오늘·지금은 인자로 받는다.
 *
 * 배는 버스와 달리 요일 구분이 없고 **날짜마다 원문**이 있다(외도유람선 예약센터 월별 배시간표).
 * 그래서 날짜마다 세 갈래를 가른다.
 *   PUBLISHED      그 날짜를 덮는 공개 범위가 있다. 편이 0이면 원문이 공개한 날에 예정된 배가 없는 것이다
 *   UNPUBLISHED    첫 수집 이후인데 공개 범위 밖이다 — 아직 안 올라왔거나 그 달 끝에 편이 없다 → 「시각 미확인」
 *   NOT_COLLECTED  첫 수집보다 앞 날짜 → 「시각 미확인」
 * 뒤의 둘을 「운행 없음」으로 말하면 기준문서 §4에서 비판한 '이유 없는 빈칸'을 우리가 하는 것이다.
 *
 * 복귀 시각은 출항 + 코스 총 소요시간이다. 원문이 10~30분 조기·지연 출항을 경고하므로 화면은 늘 「약」을 붙인다.
 */
public final class FerryTable {
  private FerryTable() {}

  /**
   * 공개 범위 한 행 = 선착장 × 달 × 수집. from~to 는 그 선착장이 그 달에 편을 가진 첫날~마지막 날.
   * fetchDate 는 수집일(한국 시간) — 원문 달력은 수집일부터 그리므로 그 앞 날짜는 그 수집이 보지 못했다.
   */
  public record Coverage(long id, LocalDate month, LocalDate from, LocalDate to, OffsetDateTime fetchedAt,
      LocalDate fetchDate) {}

  public record Sailing(long coverageId, long courseId, LocalDate date, int departMin) {}

  public record Course(long id, boolean landsOnOedo, int totalMin) {}

  public enum Status { PUBLISHED, UNPUBLISHED, NOT_COLLECTED }

  public record Slot(int departMin, long courseId, int returnMin) {}

  public record DayRow(LocalDate date, Status status, List<Slot> sailings) {}

  public record Next(long courseId, LocalDate date, int departMin, int returnMin) {}

  public record Result(List<DayRow> rows, List<Next> next) {}

  /**
   * @param coverages 한 선착장의 공개 범위 전부(수집이 여러 번이면 겹칠 수 있다 — 늦은 수집이 이긴다)
   * @param sailings  그 공개 범위들의 편
   * @param courses   **보여줄** 코스만(외도보타니아 화면이면 외도상륙 코스 하나). 없는 코스의 편은 버린다
   * @param start     첫 날짜 · {@code days} 날짜 수
   * @param asOfDate  오늘(한국 시간) · {@code asOfMin} 지금 시각(분). null 이면 그날 첫 편부터 다음 배로 친다
   */
  public static Result build(List<Coverage> coverages, List<Sailing> sailings, Map<Long, Course> courses,
      LocalDate start, int days, LocalDate asOfDate, Integer asOfMin) {
    LocalDate firstCollected = coverages.stream().map(Coverage::from).min(Comparator.naturalOrder()).orElse(null);
    var rows = new ArrayList<DayRow>();
    for (int i = 0; i < days; i++) {
      LocalDate d = start.plusDays(i);
      LocalDate month = d.withDayOfMonth(1);
      // 그 날짜를 볼 수 있었던 수집(같은 달 블록 · 수집일 이후) 중 가장 늦은 것만 본다. 늦은 수집이 그 날을 공개 범위에서
      // 뺐다면(운항사가 편을 내렸다) 옛 수집의 편을 되살리지 않고 「시각 미확인」이다(2026-09-14 리뷰).
      var cov = coverages.stream()
          .filter(c -> c.month().equals(month) && !d.isBefore(c.fetchDate()))
          .max(Comparator.comparing(Coverage::fetchedAt))
          .filter(c -> !d.isBefore(c.from()) && !d.isAfter(c.to()));
      if (cov.isEmpty()) {
        boolean afterFirst = firstCollected != null && !d.isBefore(firstCollected);
        rows.add(new DayRow(d, afterFirst ? Status.UNPUBLISHED : Status.NOT_COLLECTED, List.of()));
        continue;
      }
      long covId = cov.get().id();
      var slots = sailings.stream()
          .filter(s -> s.coverageId() == covId && s.date().equals(d) && courses.containsKey(s.courseId()))
          .sorted(Comparator.comparingInt(Sailing::departMin)
              .thenComparing(s -> !courses.get(s.courseId()).landsOnOedo())   // 같은 시각이면 외도상륙 먼저
              .thenComparingLong(Sailing::courseId))
          .map(s -> new Slot(s.departMin(), s.courseId(), s.departMin() + courses.get(s.courseId()).totalMin()))
          .toList();
      rows.add(new DayRow(d, Status.PUBLISHED, slots));
    }

    var next = new ArrayList<Next>();
    courses.values().stream()
        .sorted(Comparator.comparing((Course c) -> !c.landsOnOedo()).thenComparingLong(Course::id))
        .forEach(c -> rows.stream()
            .filter(r -> r.status() == Status.PUBLISHED && !r.date().isBefore(asOfDate))
            .flatMap(r -> r.sailings().stream()
                .filter(s -> s.courseId() == c.id())
                .filter(s -> !r.date().equals(asOfDate) || asOfMin == null || s.departMin() >= asOfMin)
                .map(s -> new Next(c.id(), r.date(), s.departMin(), s.returnMin())))
            .findFirst()
            .ifPresent(next::add));
    return new Result(List.copyOf(rows), List.copyOf(next));
  }
}
