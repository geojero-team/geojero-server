package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimeUtilTest {
  @Test void hhmm_to_min() {
    assertEquals(385, TimeUtil.hhmmToMin("06:25"));
    assertEquals(1155, TimeUtil.hhmmToMin("19:15"));
    assertThrows(IllegalArgumentException.class, () -> TimeUtil.hhmmToMin("abc"));
  }

  @Test void midnight_crossing() {
    assertEquals("19:15", TimeUtil.minToHHMM(1155));
    assertEquals("+1일 06:25", TimeUtil.minToHHMM(1440 + 385)); // 무박 일출 첫차
  }

  @Test void day_class_토일법정공휴일() { // 표지 정의
    Set<LocalDate> none = Set.of();
    assertEquals(DayClass.WEEKDAY, TimeUtil.dayClassFor(LocalDate.parse("2026-09-09"), none));
    assertEquals(DayClass.HOLIDAY, TimeUtil.dayClassFor(LocalDate.parse("2026-09-12"), none));
    assertEquals(DayClass.HOLIDAY, TimeUtil.dayClassFor(LocalDate.parse("2026-09-13"), none));
    assertEquals(DayClass.HOLIDAY,
        TimeUtil.dayClassFor(LocalDate.parse("2026-09-09"), Set.of(LocalDate.parse("2026-09-09"))));
  }
}
