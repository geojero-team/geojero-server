package com.example.geojeroserver.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

/** 순수 시간 유틸 — 내부 계산은 자정 기준 분 정수, 자정 넘김은 1440+ (engine.md). */
public final class TimeUtil {
  private TimeUtil() {}

  private static final Pattern HHMM = Pattern.compile("^([0-9]{1,2}):([0-9]{2})$");

  public static int hhmmToMin(String s) {
    var m = HHMM.matcher(s.trim());
    if (!m.matches()) throw new IllegalArgumentException("시각 형식 오류: " + s);
    return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
  }

  public static String minToHHMM(int min) {
    int day = Math.floorDiv(min, 1440);
    int m = Math.floorMod(min, 1440);
    String base = String.format("%02d:%02d", m / 60, m % 60);
    return day > 0 ? "+" + day + "일 " + base : base;
  }

  /** 표지 정의: 공휴일 = 토~일 + 법정공휴일. */
  public static DayClass dayClassFor(LocalDate date, Set<LocalDate> holidays) {
    var dow = date.getDayOfWeek();
    return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || holidays.contains(date)
        ? DayClass.HOLIDAY : DayClass.WEEKDAY;
  }
}
