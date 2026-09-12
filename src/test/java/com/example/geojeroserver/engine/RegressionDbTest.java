package com.example.geojeroserver.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.snapshot.SnapshotRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 게이트 2: 픽스처가 아닌 실 DB(파이프라인 적재본) → SnapshotRepository → 같은 회귀 케이스.
 * RegressionTest와 짝이다 — 같은 사실을 픽스처와 DB 양쪽에서 확인한다.
 *
 * 2026-09-12 판정 제거로 다시 썼다. 단언의 모양만 바뀌고 지키는 사실은 그대로다
 * (자세한 설명은 RegressionTest 주석).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegressionDbTest {
  @Autowired SnapshotRepository repo;
  Snapshot wed, sun;

  @BeforeAll
  void load() {
    wed = repo.build(LocalDate.parse("2026-09-09"));
    sun = repo.build(LocalDate.parse("2026-09-13"));
  }

  @Test void 빌더_평일_55x6_남부2_우회알림() {
    assertEquals(DayClass.WEEKDAY, wed.dayClass());
    assertEquals(6, Timetable.countTripsOfRoute(wed, "55", 0));
    assertTrue(wed.trips().stream().anyMatch(t -> t.routeNo().equals("남부2")));
    assertTrue(wed.alerts().stream()
        .anyMatch(a -> "DETOUR".equals(a.kind()) && "홍포".equals(a.stop())));
  }

  @Test void 빌더_일요일_남부전노선제외() {
    assertEquals(DayClass.HOLIDAY, sun.dayClass());
    assertTrue(sun.trips().stream().noneMatch(t -> t.routeNo().startsWith("남부")));
  }

  @Test void case1_55번_막차_1915() {
    assertEquals(TimeUtil.hhmmToMin("19:15"), Timetable.lastDeparture(wed, "고현", "해금강"));
  }

  @Test void case2_55번_6회_주말동일() {
    assertEquals(6, Timetable.countTripsOfRoute(wed, "55", 0));
    assertEquals(6, Timetable.countTripsOfRoute(sun, "55", 0));
  }

  @Test void case3_부산발_당일치기_구간이_모두_운행한다() {
    assertFalse(Timetable.rides(wed, "부산사상", "고현").isEmpty());
    assertFalse(Timetable.rides(wed, "고현", "해금강").isEmpty());
    assertFalse(Timetable.rides(wed, "해금강", "고현").isEmpty());
    assertFalse(Timetable.rides(wed, "고현", "부산사상").isEmpty());
  }

  @Test void case4_55번_첫차_0625() {
    assertEquals(TimeUtil.hhmmToMin("06:25"),
        Timetable.rides(wed, "고현", "해금강").get(0).departMin());
  }

  @Test void case5_외도풀코스_1820복귀후_1848이_있고_막차는_2005() {
    int after = TimeUtil.hhmmToMin("18:20");
    var next = Timetable.rides(wed, "해금강", "고현").stream()
        .filter(r -> r.departMin() >= after).findFirst();
    assertTrue(next.isPresent());
    assertEquals(TimeUtil.hhmmToMin("18:48"), next.get().departMin());
    assertEquals(TimeUtil.hhmmToMin("20:05"), Timetable.lastDeparture(wed, "해금강", "고현"));
  }

  @Test void case6_여차는_정류소_격자에_없다() {
    // §2는 여차 1왕복(복귀 막차 13:20)을 적지만 격자에 열이 없다 — 자세한 이유는
    // RegressionTest 같은 케이스 주석 참고. 파서 백로그 표식이다.
    assertTrue(Timetable.rides(wed, "여차", "고현").isEmpty());
    assertFalse(Timetable.hasUnknownTime(wed, "여차", "고현"));
  }

  @Test void case7a_우회알림이_홍포에_걸려있다() {
    assertTrue(wed.alerts().stream()
        .anyMatch(a -> "홍포".equals(a.stop()) && a.reason().contains("우회")));
  }

  @Test void case7b_시간표레이어로는_저구홍포가_53번_6회() {
    assertEquals(6, Timetable.rides(Timetable.subsetByRoute(wed, "53"), "저구", "홍포").size());
    assertEquals(10, Timetable.rides(wed, "저구", "홍포").size());
  }

  @Test void case8_일요일_도장포학동_운행없음() {
    assertTrue(Timetable.rides(sun, "도장포", "학동").isEmpty());
  }

  @Test void case9_학동_13에서15회() {
    long n = Timetable.countTripsServingStop(wed, "학동", 0);
    assertTrue(n >= 13 && n <= 15, "학동 접근 " + n + "회");
  }

  @Test void case10_63번은_학동_미경유() { // 원문 프로즈=EMPTY 매핑이 지키는 케이스
    assertTrue(Timetable.rides(Timetable.subsetByRoute(wed, "63"), "능포", "학동").isEmpty());
  }

  @Test void A안_운행없음과_시각미상은_다른_답이다() {
    // 이 서비스의 명제 그 자체다. 남부2는 평일에 도장포에 **선다** — 원문이 시각만 안 준다
    // (raw_text='[미확인]' = TEXT). 일요일은 노선 자체가 운휴라 정말로 없다.
    // 둘을 뭉개 '운행 없음'으로 답하면 §4에서 비판한 '이유 없는 빈칸'이 된다.
    assertTrue(Timetable.rides(wed, "도장포", "학동").isEmpty(), "시각으로는 낼 수 없다");
    assertTrue(Timetable.hasUnknownTime(wed, "도장포", "학동"), "그러나 정차는 한다");
    assertFalse(Timetable.hasUnknownTime(sun, "도장포", "학동"), "일요일은 정말로 없다");
  }
}
