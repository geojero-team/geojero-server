package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.TimeUtil;

final class Fmt {
  private Fmt() {}

  /** 분 정수 → "HH:MM" (경계 변환 — 엔진 밖에서만 문자열). */
  static String hm(Integer min) {
    return min == null ? null : TimeUtil.minToHHMM(min);
  }
}
