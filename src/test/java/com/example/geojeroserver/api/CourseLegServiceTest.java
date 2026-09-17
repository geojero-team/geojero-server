package com.example.geojeroserver.api;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.engine.SpotLayer;
import com.example.geojeroserver.snapshot.SnapshotRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 코스 구간 줄이 말하는 버스 — 그 구간을 **가장 자주 다니는 직행 노선 + 하루 운행 횟수**(2026-09-17 사용자 결정).
 *
 * 왜 — 코스에 저장된 편 사슬은 「이 순서가 버스로 이어지는가」를 확인하려고 하루짜리 한 편씩 고른 것이다.
 * 그 편의 노선을 화면에 적으면 하루 한 번 오는 버스(55-1번)를 기다리게 된다 — 같은 구간을 55번이 하루 여섯 번 다니는데.
 * 몇 시에 갈지는 사용자가 정하므로 화면에는 구간의 **대표 노선**을 적는다.
 *
 * 값은 서버 시간표(스팟 계층) 실측이다 — 평일 2026-09-14(월) · 휴일 2026-09-19(토).
 * 스팟 시간표(/api/pois/{id}/departures)의 byRoute 와 같은 규칙으로 센다(CourseApiTest 가 둘을 맞대 본다).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourseLegServiceTest {
  @Autowired SnapshotRepository repo;
  SpotLayer.Prepared mon;
  SpotLayer.Prepared sat;

  @BeforeAll
  void load() {
    mon = SpotLayer.prepare(repo.build(LocalDate.parse("2026-09-14")));
    sat = SpotLayer.prepare(repo.build(LocalDate.parse("2026-09-19")));
  }

  /**
   * 고현 → 학동은 55번 6회 · 67-1번 6회로 같다 — **짧은 쪽**(55번 40분, 67-1번 55분)을 고른다.
   * 55번은 50번대라 매일 같다(기준문서 §2) — 평일 6 · 휴일 6. 소요는 여섯 편 전부 40분(§2 확정 데이터).
   */
  @Test void 횟수가_같으면_빨리_닿는_노선이다() {
    var calc = CourseController.legService(mon, sat, "고현", "학동");
    var s = calc.service();
    assertEquals("55", s.routeNo());
    assertEquals(40, s.durationMin());
    assertEquals(40, s.durationMinLow());
    assertFalse(s.estimated(), "고현 · 학동은 둘 다 격자 칸이다");
    assertEquals(6, s.tripsWeekday());
    assertEquals(6, s.tripsHoliday());
    assertFalse(calc.holidayNoBus());
  }

  /**
   * ★ 3-12 가 탄 편은 32-2번(하루 1회)이다. 같은 구간을 33번이 하루 9회 다닌다 — 화면은 33번을 적는다.
   * 33번 소요는 편마다 45~52분이다(늦게 닿는 값이 durationMin — 스팟 시간표 byRoute 와 같은 규칙).
   * 매미성 정류장(대금교차로)은 격자에 칸이 없어 앞뒤 정류장으로 감싼 시각이다 — 추정.
   */
  @Test void 코스가_탄_편이_아니라_가장_자주_다니는_노선이다() {
    var s = CourseController.legService(mon, sat, "고현", "대금교차로").service();
    assertEquals("33", s.routeNo());
    assertEquals(52, s.durationMin());
    assertEquals(45, s.durationMinLow());
    assertTrue(s.estimated());
    assertEquals(9, s.tripsWeekday());
    assertEquals(9, s.tripsHoliday());
  }

  /** 20번대는 평일/휴일이 갈라진다(기준문서 §2) — 고현 → 지세포 23번은 평일 11회 · 휴일 6회. */
  @Test void 휴일_횟수는_휴일_시간표에서_같은_노선을_센다() {
    var s = CourseController.legService(mon, sat, "고현", "지세포").service();
    assertEquals("23", s.routeNo());
    assertEquals(11, s.tripsWeekday());
    assertEquals(6, s.tripsHoliday());
  }

  /**
   * 휴일에 **정말로** 버스가 없는 구간 — 58번 성포 → 거제는 평일 한 편(원문 runs_holiday 거짓)이고 휴일엔 이 구간을 잇는 노선이 없다.
   * 시각 미상 노선도 없다(NO_SERVICE) → holidayNoBus 참.
   */
  @Test void 휴일에_운행이_없으면_holidayNoBus_참이다() {
    var calc = CourseController.legService(mon, sat, "성포", "거제");
    assertEquals("58", calc.service().routeNo());
    assertEquals(1, calc.service().tripsWeekday());
    assertEquals(0, calc.service().tripsHoliday());
    assertTrue(calc.holidayNoBus());
  }

  /**
   * ★★ 운행 없음 ≠ 시각 미상 — 이 서비스의 명제다. 편이 0 이어도 원문이 「서지만 시각을 안 준」 노선이 있으면 holidayNoBus 는 거짓이다.
   *
   * 실데이터 휴일 시간표에는 그런 구간이 없다(휴일 스냅샷의 시각 미상 칸은 시외버스 세 노선뿐 — 2026-09-17 전수 확인).
   * 그래서 남부면 마을버스 남부4(근포 · 명사 · 탑포 칸이 전부 「[미확인]」)가 서는 **평일 시간표를 휴일 자리에** 넣어 규칙을 본다.
   * 평일에도 시각이 없으니 대표 노선도 없다(service null).
   */
  @Test void 시각_미상이면_holidayNoBus_거짓이다() {
    var calc = CourseController.legService(mon, mon, "근포", "탑포");
    assertNull(calc.service(), "시각이 없으면 노선 · 분 · 횟수를 말할 수 없다");
    assertFalse(calc.holidayNoBus(), "남부4 가 서지만 시각이 없다 — 운행 없음이 아니다");
  }
}
