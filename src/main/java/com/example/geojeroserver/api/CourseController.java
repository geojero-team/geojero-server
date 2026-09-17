package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.DayClass;
import com.example.geojeroserver.engine.Snapshot;
import com.example.geojeroserver.engine.SpotLayer;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.engine.Timetable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 코스 API — 02-2 화면(Figma 442:748)의 코스 추천 → 지도 → 코스 상세를 이 둘로 그린다.
 *
 * 2026-09-12에 DB 기반으로 다시 썼다. 전에는 §3 검증 코스 3종을 상수로 내려줄 뿐이었고
 * 구간 정보가 없었다(course_pois 0행). 지금은 V17이 적재한 추천 코스를 legs·rides까지 내려준다.
 *
 * **목록에는 추천 코스만 담는다.** §3 검증 코스 3종은 화면에 뜨지 않지만
 * saved_trips.course_id 가 가리킬 수 있어 상세 조회는 된다(구간 없이 이름·요약만).
 *
 * 2026-09-14 v3(Figma 582:416): 3/4/5곳 칩을 없애고 **대표 코스**를 카드로 보여준다 —
 * {@code featured=true}. 카드가 새로 말하는 것(제목·9경 번호·노선·하루 회차·휴일 운행)은 대표 코스가
 * 아니어도 전부 채운다.
 *
 * 2026-09-17 코스 재설계 2차 세트(V37): 대표 코스는 **사람이 고른 일곱**이고 순서는 {@code featured_rank} 다.
 * 옛 규칙(9경 많은 순 · 버스 짧은 순)은 버렸다 — 카드 배지가 전부 「거제 9경 · N경」이라 코스마다 무엇이 다른지
 * 화면이 말하지 않았다. 카드마다 어느 성격 축으로 골랐는지(badgeAxis)와 거제시 공식 코스 대조(officialCourse)가 붙는다.
 *
 * 2026-09-17 오후(사용자 결정): 코스는 **순서 + 구간마다 버스**만 제시하고 몇 시에 가서 며칠에 나눠 돌지는 사용자가 정한다.
 * 코스에 저장된 편 사슬(course_legs 시각 · course_rides)은 「이 순서가 버스로 이어지는가」를 확인하려고 고른 하루짜리 한 편씩이다 —
 * 그 편의 노선을 화면에 적으면 하루 한 번 오는 버스(55-1번)를 기다리게 된다. 그래서 BUS 구간마다 **그 구간을 가장 자주 다니는
 * 직행 노선 + 하루 운행 횟수**(legs[].service)와 휴일에 그 구간 버스가 정말로 없는지(holidayNoBus)를 따로 준다.
 * 사슬은 저장 · 확인용으로 그대로 남는다.
 */
@RestController
public class CourseController {
  /** 버스 시각의 유일한 공식 원천. 코스의 모든 숫자가 여기서 나왔다(기준문서 §2). */
  private static final String SOURCE = "거제시 BIS 원문";
  /** 모든 코스의 출발·복귀 지점. 기획 결정으로 고현터미널 고정(기준문서 §6). */
  private static final String ORIGIN_NAME = "고현터미널";
  /** 고현터미널의 시간표 정류장 이름 — 스팟 시간표(SpotTimetableController)가 쓰는 것과 같다. */
  private static final String ORIGIN_STOP = "고현";
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public record CourseDto(long courseId, String name, String theme, String summary) {}

  /** §3 검증 코스 3종 — 확정 데이터 기반 상수. DB 동기화는 CourseSeeder(saved_trips FK 원천). */
  public static final List<CourseDto> COURSES = List.of(
      new CourseDto(1, "부산발 당일치기", "ISLAND",
          "사상 07:00 → 바람의언덕·유람선 → 막차 복귀 → 부산행 21:10"),
      new CourseDto(2, "서울발 무박 일출", "NATURE",
          "서울남부 23:30 → 첫차 06:25 → 일출 → 상행 22:00"),
      new CourseDto(3, "외도 풀코스", "ISLAND",
          "도장포 막배 15:30 → 18:20 복귀 → 버스 연결"));

  // ── 응답 모양 ──────────────────────────────────────────────────────────────

  /** 카드의 원형 썸네일 하나 + 지도 핀 하나. */
  public record SpotBrief(int seq, long poiId, String name, String shortName,
      String theme, Double lat, Double lng) {}

  /**
   * 하루 회차 수 — 평일 시간표와 휴일 시간표 각각. BIS 표지의 「1일 N회」(기점발 회차)다.
   * 노선이 하나인 코스에만 붙는다 — 둘 이상이면 어느 노선의 횟수인지 말할 수 없고, 더하면
   * 실제로 타지 않는 숫자가 된다(노선을 섞어 평균 내지 않는 것과 같은 이유 — engine.md).
   */
  public record TripsPerDay(int weekday, int holiday) {}

  /**
   * 거제시 공식 관광코스와의 대조(V37 official_courses). 코스 상세 머리 한 줄 「거제시 추천 관광코스 「당일코스」 여섯 곳 중 네 곳」의 근거다.
   * 카드 사진 위 배지는 2026-09-17 저녁부터 숫자 없이 「거제시 추천 관광코스」만 적는다(사용자 결정).
   *   name       원문 코스 이름(「당일코스」 · 「2일코스」)
   *   total      원문 장소 칸 수 — 다리(거제대교 · 거가대교)와 HTML 주석 안의 칸은 세지 않는다
   *   matched    그중 이 코스 스팟이 든 칸 수
   *   orderKept  방문 순서가 원문 나열 순서와 같은가
   *   sourceUrl  원문 페이지(tour.geoje.go.kr)
   */
  public record OfficialCourse(String name, int total, int matched, boolean orderKept, String sourceUrl) {}

  /**
   * ★ {@code busMinTotal} 이 이 서비스가 소유한 숫자다 — 구간 이동시간의 합이다.
   *
   * {@code approxTotalMin}(약 8시간 30분)은 출발부터 복귀까지의 **경과 시간**이라
   * 머무는 시간이 대부분이다(3-01: 버스 114분 + 머무는 401분). 얼마나 머물지는
   * 사용자가 정하는 것이라 2026-09-13에 화면에서 뺐다 — 컬럼과 필드는 남긴다.
   *
   * v3 카드(2026-09-14)가 더 말하는 것:
   *   title · intro     — V28 의 제목·소개(대표 10개에만 있다, 없으면 null)
   *   nineScenicNos     — 코스 스팟의 거제 9경 번호(pois.nine_scenic_no, 오름차순). ⚠️ nineScenicCount 는
   *                       팀원 적재값(V20)이라 매미성이 든 코스에서 하나 적다(CourseDataTest)
   *   busRoutes         — 탄 노선 번호, 구간·승차 순서대로 중복 없이
   *   tripsPerDay       — 노선이 하나일 때만(위 TripsPerDay)
   *   holidayService    — 모든 승차가 휴일 시간표에도 같은 시각으로 있는가
   *
   * 코스 재설계 2차 세트(2026-09-17, V37)가 더 말하는 것 — 대표가 아닌 코스는 셋 다 null 이다:
   *   featuredRank      — 대표 목록 순서(사람이 고른 순서)
   *   badgeAxis         — 어느 성격 축으로 골랐나: OFFICIAL 거제시 공식 코스 · THEME 분류 · NINE 거제 9경.
   *                       분류 구성은 spots[].theme, 9경은 nineScenicNos, 배는 ferryMinTotal 로 화면이 센다
   *   officialCourse    — badgeAxis 가 OFFICIAL 일 때만(위 OfficialCourse)
   *
   * 2026-09-17 오후 — 구간 대표 노선(클래스 주석):
   *   busMinTotal       — **BUS 구간 legs[].service.durationMin 의 합**이다. 코스 상세 구간 줄의 분을 더하면 이 숫자다.
   *                       전에는 저장된 편 사슬의 분(course_legs.duration_min)이었다
   *   holidayNoBusLegs  — 휴일에 그 구간 버스가 정말로 없는(holidayNoBus) 구간, 방문 순서. 없으면 []. 화면의 옛 「평일만 / 평일·휴일」
   *                       태그를 대신한다 — holidayService 는 사슬이 우연히 탄 한 편이 휴일에 있는가라 「휴일엔 못 가는 코스」로 읽혔다
   *   holidayService · tripsPerDay 는 옛 클라 호환으로 남긴다(화면이 더 쓰지 않는다)
   */
  public record CourseCard(long courseId, String courseCode, int spotCount, int rank,
      int nineScenicCount, String name, String summary,
      String title, String intro,
      Integer featuredRank, String badgeAxis, OfficialCourse officialCourse,
      List<Integer> nineScenicNos, List<String> busRoutes,
      TripsPerDay tripsPerDay, boolean holidayService,
      String departAt, String returnAt, int approxTotalMin, String approxTotalText,
      int busMinTotal, String busTotalText,
      int ferryMinTotal, String ferryTotalText,
      List<LegName> holidayNoBusLegs,
      List<SpotBrief> spots) {}

  /** 휴일에 버스가 없는 구간 하나 — 카드 태그 「휴일엔 버스 없는 구간이 있어요」의 근거. 고현터미널은 이름 그대로 온다. */
  public record LegName(String fromName, String toName) {}

  /**
   * 한 BUS 구간을 **가장 자주 다니는 직행 노선**(같은 노선 · 같은 방향 한 편, 환승 없음)과 그 하루 횟수.
   *
   * 스팟 시간표(/api/pois/{id}/departures)와 같은 엔진 · 같은 스팟 계층 · 같은 byRoute 로 센다 — 「시간표 ›」와 숫자가 같아야 한다.
   *   routeNo        평일에 가장 편이 많은 노선. 같으면 durationMin 짧은 쪽, 그래도 같으면 노선 번호 순
   *   durationMin    그 노선 소요의 늦게 닿는 쪽(최댓값) — byRoute.durationMin 과 같은 규칙. 몇 시 편을 탈지는 사용자가 정한다
   *   durationMinLow 가장 빠른 값(같으면 durationMin 과 같다)
   *   estimated      그 노선 편 가운데 하나라도 앞뒤 정류장으로 감싼 시각인가(스팟 시간표 화면이 소요시간에 추정을 붙이는 규칙과 같다)
   *   tripsWeekday   그 노선이 평일에 이 구간을 잇는 편 수
   *   tripsHoliday   휴일 편 수(0 가능)
   */
  public record LegService(String routeNo, int durationMin, int durationMinLow, boolean estimated,
      int tripsWeekday, int tripsHoliday) {}

  /**
   * 구간 하나의 계산 결과 — 대표 노선(평일에 시각 있는 편이 없으면 null)과 휴일 운행 없음.
   * holidayNoBus 는 휴일 편이 어느 노선으로도 없고 **그게 시각 미상이 아닐 때만** 참이다(운행 없음 ≠ 시각 미상 — 절대규칙 3).
   */
  record LegCalc(LegService service, boolean holidayNoBus) {}

  public record CoursesRes(Map<String, Integer> counts, List<CourseCard> courses) {}

  /** course_rides 한 줄 — 카드의 노선 목록과 휴일 운행 판정에 쓴다. 시각은 자정 기준 분. */
  private record RideRow(String routeNo, String boardStop, int boardMin,
      String alightStop, int alightMin) {}

  /**
   * 평일 스냅샷과 휴일 스냅샷 한 쌍. 하루 회차 수·휴일 운행 · 구간 대표 노선이 여기서 나온다.
   * weekdayRides · holidayRides 는 경로 문장을 한 번만 읽어 둔 것이고, legs 는 (타는 정류장>내리는 정류장) → 계산 결과 캐시다.
   * 스냅샷이 같은 동안(SnapshotService 가 같은 객체를 주는 동안) 답이 같으므로 스냅샷이 바뀔 때 통째로 버린다.
   */
  private record Days(Snapshot weekday, Snapshot holiday,
      SpotLayer.Prepared weekdayRides, SpotLayer.Prepared holidayRides, Map<String, LegCalc> legs) {}

  /** 코스 상세의 스팟 한 곳 — 체류 시각이 붙는다. */
  public record CourseStop(int seq, long poiId, String name, String shortName,
      String theme, Double lat, Double lng,
      String arriveAt, String leaveAt, Integer stayMin) {}

  /** 구간 안에서 실제로 탄 버스. SAME_STOP 구간은 이 목록이 빈다. */
  public record Ride(String routeNo, String boardStop, String boardAt, boolean boardEstimated,
      String alightStop, String alightAt, boolean alightEstimated) {}

  /**
   * 정류장 하나와 거기서 스팟까지의 직선거리(m).
   *
   * 버스가 서는 곳은 스팟이 아니라 정류장이다 — 화면이 「55번 · 10분」만 적으면 「10분 뒤 스팟 도착」으로 읽힌다.
   * 해금강은 내리는 정류장에서 직선 1.1km 다(2026-09-16 사용자 결정 — 디자인브리프 부록 H).
   * 값은 타는 곳 표(boarding_stops, V26·V34)에서 오고 {@code (from_poi_id, to_poi_id, route_no)} 로 찾는다 —
   * 코스 구간의 키와 모양이 같다. 거리는 **직선**이고 걷는 거리가 아니다(걷는 거리·시간은 어느 원문에도 없다 — 절대규칙 1).
   */
  public record StopWalk(String stop, Integer distanceM) {}

  /** 한 구간·노선의 타는 곳과 내리는 곳. 표의 한 줄에 둘 다 들어 있다. */
  private record StopPair(StopWalk board, StopWalk alight) {}

  /**
   * 코스의 한 구간. {@code estimated} 는 "이 구간 시각 중 하나라도 추정인가"다 —
   * 화면이 배지 하나만 그리면 되게 서버가 접어서 준다. 근거는 rides 안에 남아 있다.
   * {@code alight} 는 가는 곳이 스팟일 때만 있다 — 고현터미널로 돌아가는 마지막 구간은 null 이다.
   * {@code board} 는 출발 쪽이 스팟일 때만 있다 — 고현터미널에서 떠나는 첫 구간은 null 이다(터미널이 곧 정류장이라 0m 다).
   * 화면은 한 줄만 그린다: 내리는 곳이 있으면 그것, 없으면(마지막 구간) 타는 곳 — 그 구간과 스팟의 관계를 말한다.
   */
  /**
   * 이 구간이 타는 유람선 편(V35·V36). {@code mode == "FERRY"} 일 때만 있다.
   *
   * 값은 전부 {@code ferry_courses} 원문이다 — 우리가 계산한 것이 하나도 없다.
   *   courseName   상품 원문 코스명. **「외도입장료 별도」가 여기 있다** — 화면 어딘가에 남아야 한다(부록 G)
   *   legendLabel  짧은 이름(「외도상륙+해금강선상관광」). 구간 줄에 적는다
   *   totalText    총 소요시간(「약 2시간 40분」). ⚠️ **왕복 + 외도 체류를 합친 값**이다 —
   *                한 방향이 몇 분인지는 원문에 없어서 가는 구간에 싣고 돌아오는 구간은 0분이다
   *   stayMin      외도에 내려 머무는 분(선상관광 편은 내리지 않아 null)
   *   landsOnOedo  외도에 내리는가. false 면 배에서 보고 돌아온다
   *   dockName     타는 선착장 짧은 이름(「도장포」)
   *
   * ⚠️ **출항 시각은 여기 없다.** 배는 날짜마다 시각이 달라(도장포 외도상륙 편은 48일 중 10:30 이 38일 ·
   * 14:00 이 36일, 나머지는 제각각) 코스에 박지 않는다. 그날 배편은 스팟의 「시간표 ›」가 보여준다(부록 G).
   */
  public record FerryRide(String legendLabel, String courseName, String totalText,
      Integer stayMin, boolean landsOnOedo, String dockName, String bookingUrl) {}

  /**
   * {@code service} · {@code holidayNoBus} 는 BUS 구간에만 있다(FERRY · SAME_STOP 은 둘 다 null) — LegService · LegCalc 주석.
   * {@code board} · {@code alight} 는 **service.routeNo 의 정류장**이다 — 구간 줄이 적는 노선을 기다릴 곳이라서.
   * 대표 노선이 없으면(옛 폴백) 탄 편의 노선으로 찾는다.
   */
  public record Leg(int seq, String mode, Long fromPoiId, String fromName,
      Long toPoiId, String toName, String departAt, String arriveAt,
      int durationMin, int transfers, int transferWaitMin,
      boolean estimated, List<Ride> rides, StopWalk board, StopWalk alight,
      FerryRide ferry, LegService service, Boolean holidayNoBus) {}

  /**
   * featuredRank · badgeAxis · officialCourse 는 카드(CourseCard)와 같은 값 · 같은 모양이다 — 대표가 아닌 코스는 셋 다 null.
   * 카드 사진 위 배지는 숫자 없는 이름(「거제시 추천 관광코스」)만 적고, 원문 코스 이름 · 몇 곳 중 몇 곳 · 원문 순서는
   * 코스 상세 머리 한 줄이 이 officialCourse 로 적는다(2026-09-17 저녁 사용자 결정).
   */
  public record CourseDetail(long courseId, String courseCode, String name, String summary,
      String title, String intro,
      Integer featuredRank, String badgeAxis, OfficialCourse officialCourse,
      String theme, Integer spotCount, Integer nineScenicCount,
      String departAt, String returnAt, Integer totalMin, Integer approxTotalMin,
      String approxTotalText, int busMinTotal, String busTotalText,
      int ferryMinTotal, String ferryTotalText,
      List<LegName> holidayNoBusLegs,
      int legCount, int estimatedLegCount,
      String service, String baseDate, String source,
      String originName, String originStop,
      List<CourseStop> stops, List<Leg> legs) {}

  private final JdbcTemplate jdbc;
  private final SnapshotService snapshots;
  /** 마지막으로 쓴 평일 · 휴일 한 쌍과 그 구간 계산 캐시. 스냅샷이 바뀌면 새로 만든다(weekdayAndHoliday). */
  private volatile Days days;

  public CourseController(JdbcTemplate jdbc, SnapshotService snapshots) {
    this.jdbc = jdbc;
    this.snapshots = snapshots;
  }

  // ── 목록 ──────────────────────────────────────────────────────────────────

  /**
   * @param spotCount 3·4·5 로 거른다. 없으면 전량.
   * @param featured  true 면 대표 코스만(featured_rank 가 있는 코스) — **featured_rank 순서 그대로**(V37).
   *                  spotCount 와 같이 오면 둘 다 거른다.
   */
  @GetMapping("/api/courses")
  public CoursesRes courses(@RequestParam(required = false) Integer spotCount,
      @RequestParam(required = false) Boolean featured) {
    // 칩은 코스가 0개여도 개수를 보여줘야 하므로 필터와 별개로 전량을 센다.
    var counts = new LinkedHashMap<String, Integer>();
    for (int n : new int[] {3, 4, 5}) {
      counts.put(String.valueOf(n), jdbc.queryForObject("""
          SELECT count(*) FROM courses
          WHERE course_code IS NOT NULL AND enabled AND spot_count = ?""",
          Integer.class, n));
    }

    // 파라미터를 `? IS NULL` 로 비교하면 Postgres가 타입을 못 정한다(500). SQL을 갈라 만든다.
    boolean featuredOnly = Boolean.TRUE.equals(featured);
    String sql = """
        SELECT c.course_id, c.course_code, c.spot_count, c.rank_no, c.nine_scenic_count,
               c.course_name, c.summary, c.title, c.intro, c.depart_time, c.return_time, c.approx_total_min,
               c.featured_rank, c.badge_axis, c.official_matched, c.official_order_kept,
               o.name AS official_name, o.place_count AS official_total, o.source_url AS official_source_url
        FROM courses c
        LEFT JOIN official_courses o ON o.official_code = c.official_code
        WHERE c.course_code IS NOT NULL AND c.enabled"""
        + (featuredOnly ? " AND c.featured_rank IS NOT NULL" : "")
        + (spotCount == null ? "" : " AND c.spot_count = ?")
        // 대표 목록은 사람이 고른 순서다 — 옛 「9경 많은 순 · 버스 짧은 순」 정렬은 V37 에서 버렸다.
        + (featuredOnly ? " ORDER BY c.featured_rank" : " ORDER BY c.spot_count, c.rank_no");
    var rows = spotCount == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, spotCount);

    // 구간을 한 번에 받아 둔다 — 카드마다 물으면 코스 수만큼 쿼리가 늘어난다.
    var ferryMin = ferryMinByCourse();
    var days = weekdayAndHoliday();
    var legSums = legSumsByCourse(days);

    // 노선·9경 번호도 한 번에 받아 course_id 로 묶는다(legSumsByCourse 와 같은 방식).
    var rides = ridesByCourse();
    var nineNos = nineScenicNosByCourse();

    var cards = new ArrayList<CourseCard>();
    for (var c : rows) {
      long id = num(c.get("course_id"));
      int approx = (int) num(c.get("approx_total_min"));
      var sums = legSums.getOrDefault(id, LegSums.EMPTY);
      int bus = sums.busMin();
      var courseRides = rides.getOrDefault(id, List.of());
      var routes = busRoutes(courseRides);
      cards.add(new CourseCard(id, (String) c.get("course_code"),
          (int) num(c.get("spot_count")), (int) num(c.get("rank_no")),
          (int) num(c.get("nine_scenic_count")),
          (String) c.get("course_name"), (String) c.get("summary"),
          (String) c.get("title"), (String) c.get("intro"),
          (Integer) c.get("featured_rank"), (String) c.get("badge_axis"), officialCourse(c),
          nineNos.getOrDefault(id, List.of()), routes,
          tripsPerDay(routes, days), holidayService(courseRides, days.holiday()),
          hm(c.get("depart_time")), hm(c.get("return_time")),
          approx, approxText(approx),
          bus, approxText(bus),
          ferryMin.getOrDefault(id, 0),
          ferryMin.getOrDefault(id, 0) == 0 ? null : approxText(ferryMin.get(id)),
          sums.holidayNoBusLegs(),
          spotBriefs(id)));
    }
    return new CoursesRes(counts, cards);
  }

  /** 코스 한 개의 버스 분 합(구간 대표 노선 기준)과 휴일에 버스 없는 구간들. */
  private record LegSums(int busMin, List<LegName> holidayNoBusLegs) {
    static final LegSums EMPTY = new LegSums(0, List.of());
  }

  /**
   * course_id → 버스 분 합 · 휴일 버스 없는 구간. 구간이 없는 코스(§3 검증 코스)는 키가 없다.
   *
   * ★ **배를 버스로 세지 않는다.** 카드가 적는 「버스 약 N분」은 버스에 앉아 있는 분이고, 배 160분을 거기 더하면 거짓말이 된다.
   * 같은 정류장 구간도 버스가 아니라 세지 않는다(0분이다). 코스 상세(course)가 legs 로 더하는 것과 같은 규칙이다.
   */
  private Map<Long, LegSums> legSumsByCourse(Days d) {
    var bus = new LinkedHashMap<Long, Integer>();
    var noBus = new LinkedHashMap<Long, List<LegName>>();
    jdbc.query("""
        SELECT l.course_id, l.mode, l.from_poi_id, l.to_poi_id, l.duration_min,
               COALESCE(pf.short_name, pf.poi_name) AS from_name, COALESCE(pt.short_name, pt.poi_name) AS to_name,
               pf.timetable_stop AS from_stop, pt.timetable_stop AS to_stop
        FROM course_legs l
        LEFT JOIN pois pf ON pf.poi_id = l.from_poi_id
        LEFT JOIN pois pt ON pt.poi_id = l.to_poi_id
        ORDER BY l.course_id, l.leg_seq""",
        rs -> {
          long id = rs.getLong("course_id");
          bus.putIfAbsent(id, 0);
          noBus.computeIfAbsent(id, k -> new ArrayList<>());
          String mode = rs.getString("mode");
          if (!"BUS".equals(mode)) return;
          Long fromPoi = rs.getObject("from_poi_id", Long.class);
          Long toPoi = rs.getObject("to_poi_id", Long.class);
          var calc = legCalc(d, mode, fromPoi, rs.getString("from_stop"), toPoi, rs.getString("to_stop"));
          bus.merge(id, busMinOf(calc, rs.getInt("duration_min")), Integer::sum);
          if (calc != null && calc.holidayNoBus()) {
            noBus.get(id).add(new LegName(fromPoi == null ? ORIGIN_NAME : rs.getString("from_name"),
                toPoi == null ? ORIGIN_NAME : rs.getString("to_name")));
          }
        });
    var out = new LinkedHashMap<Long, LegSums>();
    for (var e : bus.entrySet()) out.put(e.getKey(), new LegSums(e.getValue(), List.copyOf(noBus.get(e.getKey()))));
    return out;
  }

  /**
   * 버스 구간 하나의 분 — 구간 줄이 적는 대표 노선의 늦게 닿는 분. 대표 노선이 없으면(평일에 시각 있는 편이 없다)
   * 저장된 편 사슬의 분으로 채운다 — 지금 추천 코스 30개엔 그런 구간이 없다(CourseApiTest 가 지킨다).
   */
  private static int busMinOf(LegCalc calc, int storedMin) {
    return calc != null && calc.service() != null ? calc.service().durationMin() : storedMin;
  }

  /**
   * BUS 구간의 대표 노선 · 휴일 운행 없음. 버스가 아니면 null(배 · 같은 정류장 구간).
   * 구간 끝이 NULL 이면 고현터미널이고, 스팟은 그 스팟의 시간표 정류장(pois.timetable_stop)으로 묻는다 — 스팟 시간표와 같은 정류장이다.
   */
  private LegCalc legCalc(Days d, String mode, Long fromPoi, String fromStop, Long toPoi, String toStop) {
    if (!"BUS".equals(mode)) return null;
    String from = fromPoi == null ? ORIGIN_STOP : fromStop;
    String to = toPoi == null ? ORIGIN_STOP : toStop;
    if (from == null || to == null) return null; // 격자에 정류장 칸이 없는 스팟 — 버스 구간에는 오지 않는다
    return d.legs().computeIfAbsent(from + ">" + to,
        k -> legService(d.weekdayRides(), d.holidayRides(), from, to));
  }

  /**
   * ★ 구간을 가장 자주 다니는 직행 노선과 휴일 운행 없음 — 순수 계산(스냅샷 둘과 정류장 이름만 받는다).
   *
   * 스팟 시간표가 세는 그대로 센다: 스팟 계층(SpotLayer)의 승차 → SpotTimetableController.byRoute(노선별 횟수 · 소요 폭).
   * 새로 세는 규칙을 두지 않는다 — 「시간표 ›」와 한 숫자라도 어긋나면 구간 줄이 거짓말을 한다.
   *
   * holidayNoBus — 휴일 승차가 어느 노선으로도 없고, 원문 격자에 「서지만 시각이 없는」 노선도 없을 때만 참이다.
   * 스팟 시간표의 emptyReason 이 NO_SERVICE 인 것과 같은 말이다. 시각 미상(UNKNOWN_TIME)을 「버스 없음」이라 적으면
   * 지도앱이 재난 운휴를 이유 없는 빈칸으로 그린 것과 같은 잘못이다(기준문서 §4 · 절대규칙 3).
   */
  static LegCalc legService(SpotLayer.Prepared weekday, SpotLayer.Prepared holiday, String from, String to) {
    var wd = SpotLayer.rides(weekday, from, to);
    var hd = SpotLayer.rides(holiday, from, to);
    boolean noBus = hd.isEmpty() && Timetable.unknownTimeRoutes(holiday.snapshot(), from, to).isEmpty();
    if (wd.isEmpty()) return new LegCalc(null, noBus);

    var best = SpotTimetableController.byRoute(wd.stream().map(SpotTimetableController::departure).toList())
        .stream()
        .min(java.util.Comparator
            .comparing(SpotTimetableController.RouteSummary::count, java.util.Comparator.reverseOrder())
            .thenComparing(SpotTimetableController.RouteSummary::durationMin,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
            .thenComparing(SpotTimetableController.RouteSummary::routeNo))
        .orElseThrow();
    String route = best.routeNo();
    int holidayTrips = SpotTimetableController.byRoute(hd.stream().map(SpotTimetableController::departure).toList())
        .stream().filter(r -> r.routeNo().equals(route)).mapToInt(SpotTimetableController.RouteSummary::count)
        .findFirst().orElse(0);
    boolean estimated = wd.stream().anyMatch(r -> r.routeNo().equals(route) && r.estimated());
    return new LegCalc(new LegService(route, best.durationMin(), best.durationMinLow(), estimated,
        best.count(), holidayTrips), noBus);
  }

  /** 목록 한 줄 · 상세의 거제시 공식 코스 대조. 가리키는 코스가 없으면 null(V37 CHECK 가 OFFICIAL 축과 묶는다). */
  private static OfficialCourse officialCourse(Map<String, Object> c) {
    if (c.get("official_name") == null) return null;
    return new OfficialCourse((String) c.get("official_name"), (int) num(c.get("official_total")),
        (int) num(c.get("official_matched")), Boolean.TRUE.equals(c.get("official_order_kept")),
        (String) c.get("official_source_url"));
  }

  /** course_id → 배 구간 시간 합(분). 배가 없는 코스는 키가 없다. 버스 분은 legSumsByCourse 가 따로 센다. */
  private Map<Long, Integer> ferryMinByCourse() {
    var out = new LinkedHashMap<Long, Integer>();
    jdbc.query("SELECT course_id, COALESCE(sum(duration_min), 0) AS min_sum"
        + " FROM course_legs WHERE mode = 'FERRY' GROUP BY course_id",
        rs -> {
          out.put(rs.getLong("course_id"), rs.getInt("min_sum"));
        });
    return out;
  }

  /** course_id → 탄 버스(구간·승차 순). 승차가 없는 코스(§3 검증 코스)는 키가 없다. */
  private Map<Long, List<RideRow>> ridesByCourse() {
    var out = new LinkedHashMap<Long, List<RideRow>>();
    jdbc.query("""
        SELECT l.course_id, r.route_no, r.board_stop, r.board_time, r.alight_stop, r.alight_time
        FROM course_rides r JOIN course_legs l ON l.leg_id = r.leg_id
        ORDER BY l.course_id, l.leg_seq, r.ride_seq""",
        rs -> {
          out.computeIfAbsent(rs.getLong("course_id"), k -> new ArrayList<>())
              .add(new RideRow(rs.getString("route_no"),
                  rs.getString("board_stop"), minutes(rs.getObject("board_time")),
                  rs.getString("alight_stop"), minutes(rs.getObject("alight_time"))));
        });
    return out;
  }

  /** course_id → 코스 스팟의 거제 9경 번호(오름차순). 9경이 없는 코스는 키가 없다. */
  private Map<Long, List<Integer>> nineScenicNosByCourse() {
    var out = new LinkedHashMap<Long, List<Integer>>();
    jdbc.query("""
        SELECT cp.course_id, p.nine_scenic_no
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE p.nine_scenic_no IS NOT NULL
        ORDER BY cp.course_id, p.nine_scenic_no""",
        rs -> {
          out.computeIfAbsent(rs.getLong("course_id"), k -> new ArrayList<>())
              .add(rs.getInt("nine_scenic_no"));
        });
    return out;
  }

  /**
   * 오늘(KST) 이후 첫 평일과 첫 휴일의 스냅샷. 어느 평일·어느 휴일이든 시간표는 같다 — trips 의
   * runs_weekday/runs_holiday 플래그로 갈리기 때문이다. 날짜를 박아두지 않는 이유는 시간표 버전이
   * 날짜로 유효기간을 갖기 때문이다(개편 뒤 옛 판을 읽게 된다). 공휴일은 holidays 표를 본다 —
   * 추석(2026-09-25 금)을 평일로 읽으면 휴일 시간표를 평일 회차라고 말하게 된다.
   */
  private Days weekdayAndHoliday() {
    Set<LocalDate> holidays = new HashSet<>(jdbc.query("SELECT holiday_date FROM holidays",
        (rs, i) -> rs.getObject("holiday_date", LocalDate.class)));
    LocalDate today = LocalDate.now(KST);
    var weekday = snapshots.forDate(firstOf(DayClass.WEEKDAY, today, holidays).toString());
    var holiday = snapshots.forDate(firstOf(DayClass.HOLIDAY, today, holidays).toString());
    // 스냅샷이 그대로면 구간 계산 캐시도 그대로다. 날이 바뀌거나 SnapshotService 가 다시 읽으면 새 객체라 새로 만든다.
    var cached = days;
    if (cached != null && cached.weekday() == weekday && cached.holiday() == holiday) return cached;
    var fresh = new Days(weekday, holiday, SpotLayer.prepare(weekday), SpotLayer.prepare(holiday),
        new java.util.concurrent.ConcurrentHashMap<>());
    days = fresh;
    return fresh;
  }

  private static LocalDate firstOf(DayClass want, LocalDate from, Set<LocalDate> holidays) {
    var d = from;
    while (TimeUtil.dayClassFor(d, holidays) != want) d = d.plusDays(1);
    return d;
  }

  /** 코스가 탄 노선 번호 — 구간·승차 순서대로, 중복 없이(55번을 두 번 타도 한 번). */
  private static List<String> busRoutes(List<RideRow> rides) {
    var out = new LinkedHashSet<String>();
    for (var r : rides) out.add(r.routeNo());
    return List.copyOf(out);
  }

  /**
   * 하루 회차 수 — 노선이 하나일 때만(TripsPerDay 주석). 기점발(direction 0) 회차 = BIS 표지의
   * 「1일 N회」. 55번은 6이다(기준문서 §2, 주말 동일).
   */
  private static TripsPerDay tripsPerDay(List<String> routes, Days days) {
    if (routes.size() != 1) return null;
    String route = routes.get(0);
    return new TripsPerDay((int) Timetable.countTripsOfRoute(days.weekday(), route, 0),
        (int) Timetable.countTripsOfRoute(days.holiday(), route, 0));
  }

  /**
   * 코스의 **모든** 승차가 휴일 시간표에도 있는가 — 같은 노선·같은 승차·같은 하차 시각으로.
   *
   * CourseTimetableConsistencyTest 가 평일 스냅샷에 하는 대조를 휴일 스냅샷에 되풀이하는 것이다.
   * 스팟 계층(SpotLayer)을 읽어야 도장포처럼 격자에 칸이 없는 정류장의 감싼 시각까지 같은 규칙으로
   * 나온다. 노선만 보면 틀린다 — 10·20번대는 평일/휴일 시간표가 갈라져 노선은 있어도 그 회차가
   * 없다(§2 요일 구조). 승차가 없는 코스는 "휴일에도 탄다"고 말할 근거가 없으니 거짓이다.
   */
  private static boolean holidayService(List<RideRow> rides, Snapshot holiday) {
    if (rides.isEmpty()) return false;
    for (var r : rides) {
      // 그 노선만 남긴다 — 회차마다 경로 문장을 다시 읽는 비용을 코스 수 × 노선 수로 묶는다.
      var onRoute = Timetable.subsetByRoute(holiday, r.routeNo());
      boolean found = SpotLayer.rides(onRoute, r.boardStop(), r.alightStop()).stream()
          .anyMatch(x -> x.departMin() == r.boardMin() && x.arriveMin() == r.alightMin());
      if (!found) return false;
    }
    return true;
  }

  private List<SpotBrief> spotBriefs(long courseId) {
    return jdbc.query("""
        SELECT cp.poi_seq, p.poi_id, p.poi_name, p.short_name, p.theme, p.lat, p.lng
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE cp.course_id = ? ORDER BY cp.poi_seq""",
        (rs, i) -> new SpotBrief(rs.getInt("poi_seq"), rs.getLong("poi_id"),
            rs.getString("poi_name"), rs.getString("short_name"), rs.getString("theme"),
            dbl(rs.getObject("lat")), dbl(rs.getObject("lng"))),
        courseId);
  }

  // ── 상세 ──────────────────────────────────────────────────────────────────

  /** 고현터미널 poi. 코스 구간은 터미널을 NULL 로 적지만 타는 곳 표는 그 행의 id 로 적는다. */
  private Long terminalPoiId() {
    var ids = jdbc.queryForList(
        "SELECT poi_id FROM pois WHERE poi_kind = 'TERMINAL' ORDER BY poi_id LIMIT 1", Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private static String stopKey(Long fromPoi, Long toPoi, String routeNo) {
    return fromPoi + ">" + toPoi + ">" + routeNo;
  }

  /**
   * 이 코스가 지나는 (출발, 가는 곳)의 노선마다 타는 · 내리는 정류장. 구간마다 쿼리를 쏘지 않게 한 번에 받는다.
   * 노선을 거르지 않는다 — 구간 줄이 적는 노선(대표 노선)은 코스가 탄 편의 노선과 다를 수 있다(2026-09-17).
   * 표에 그 조합이 없으면 그냥 없다 — 가까운 정류장을 추측으로 고르지 않는다(절대규칙 1).
   */
  private Map<String, StopPair> stopsOf(long courseId, Long terminal) {
    var out = new HashMap<String, StopPair>();
    jdbc.query("""
        SELECT b.from_poi_id, b.to_poi_id, b.route_no,
               b.stop_name, b.distance_m, b.alight_stop_name, b.alight_distance_m
        FROM boarding_stops b
        WHERE b.status = 'RESOLVED'
          AND EXISTS (SELECT 1 FROM course_legs l
                      WHERE l.course_id = ?
                        AND COALESCE(l.from_poi_id, ?) = b.from_poi_id
                        AND COALESCE(l.to_poi_id, ?) = b.to_poi_id)""",
        rs -> {
          out.put(stopKey(rs.getLong("from_poi_id"), rs.getLong("to_poi_id"), rs.getString("route_no")),
              new StopPair(
                  new StopWalk(rs.getString("stop_name"), (Integer) rs.getObject("distance_m")),
                  new StopWalk(rs.getString("alight_stop_name"), (Integer) rs.getObject("alight_distance_m"))));
        }, courseId, terminal, terminal);
    return out;
  }

  @GetMapping("/api/courses/{courseId}")
  public CourseDetail course(@PathVariable long courseId) {
    // 거제시 공식 코스는 목록(courses)과 같은 조인 · 같은 별칭으로 읽는다 — officialCourse(c) 를 그대로 쓰려고.
    var rows = jdbc.queryForList("""
        SELECT c.course_id, c.course_code, c.course_name, c.summary, c.title, c.intro, c.theme, c.spot_count,
               c.nine_scenic_count, c.depart_time, c.return_time, c.total_min, c.approx_total_min,
               c.service, c.base_date, c.origin_stop,
               c.featured_rank, c.badge_axis, c.official_matched, c.official_order_kept,
               o.name AS official_name, o.place_count AS official_total, o.source_url AS official_source_url
        FROM courses c
        LEFT JOIN official_courses o ON o.official_code = c.official_code
        WHERE c.course_id = ? AND c.enabled""", courseId);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 코스");
    }
    var c = rows.get(0);

    var stops = jdbc.query("""
        SELECT cp.poi_seq, p.poi_id, p.poi_name, p.short_name, p.theme, p.lat, p.lng,
               cp.arrive_time, cp.leave_time, cp.stay_min
        FROM course_pois cp JOIN pois p ON p.poi_id = cp.poi_id
        WHERE cp.course_id = ? ORDER BY cp.poi_seq""",
        (rs, i) -> new CourseStop(rs.getInt("poi_seq"), rs.getLong("poi_id"),
            rs.getString("poi_name"), rs.getString("short_name"), rs.getString("theme"),
            dbl(rs.getObject("lat")), dbl(rs.getObject("lng")),
            hm(rs.getObject("arrive_time")), hm(rs.getObject("leave_time")),
            (Integer) rs.getObject("stay_min")),
        courseId);

    Long terminal = terminalPoiId();
    var stopPairs = stopsOf(courseId, terminal);
    var days = weekdayAndHoliday();

    var legs = new ArrayList<Leg>();
    for (var l : jdbc.queryForList("""
        SELECT l.leg_id, l.leg_seq, l.mode, l.from_poi_id, l.to_poi_id,
               l.depart_time, l.arrive_time, l.duration_min, l.transfers, l.transfer_wait_min,
               COALESCE(pf.short_name, pf.poi_name) AS from_name,
               COALESCE(pt.short_name, pt.poi_name) AS to_name,
               pf.timetable_stop AS from_stop, pt.timetable_stop AS to_stop,
               fc.legend_label, fc.course_name AS ferry_course_name, fc.total_text,
               fc.oedo_stay_min, fc.lands_on_oedo, fc.booking_url, fd.short_name AS dock_name
        FROM course_legs l
        LEFT JOIN pois pf ON pf.poi_id = l.from_poi_id
        LEFT JOIN pois pt ON pt.poi_id = l.to_poi_id
        LEFT JOIN ferry_courses fc ON fc.course_id = l.ferry_course_id
        LEFT JOIN ferry_docks   fd ON fd.dock_id   = fc.dock_id
        WHERE l.course_id = ? ORDER BY l.leg_seq""", courseId)) {
      long legId = num(l.get("leg_id"));
      var rides = jdbc.query("""
          SELECT route_no, board_stop, board_time, board_estimated,
                 alight_stop, alight_time, alight_estimated
          FROM course_rides WHERE leg_id = ? ORDER BY ride_seq""",
          (rs, i) -> new Ride(rs.getString("route_no"), rs.getString("board_stop"),
              hm(rs.getObject("board_time")), rs.getBoolean("board_estimated"),
              rs.getString("alight_stop"), hm(rs.getObject("alight_time")),
              rs.getBoolean("alight_estimated")),
          legId);
      boolean est = rides.stream().anyMatch(r -> r.boardEstimated() || r.alightEstimated());
      String mode = (String) l.get("mode");
      Long fromPoi = l.get("from_poi_id") == null ? null : num(l.get("from_poi_id"));
      Long toPoi = l.get("to_poi_id") == null ? null : num(l.get("to_poi_id"));
      var calc = legCalc(days, mode, fromPoi, (String) l.get("from_stop"), toPoi, (String) l.get("to_stop"));
      LegService service = calc == null ? null : calc.service();
      // 정류장 줄의 노선은 **구간 줄이 적는 노선**(대표 노선)이다 — 사용자가 기다릴 버스의 정류장이라서.
      // 대표 노선이 없으면 옛 규칙대로 마지막으로 탄 버스다(환승은 코스에 없지만 있어도 내리는 것은 마지막 버스다).
      String stopRoute = service != null ? service.routeNo()
          : rides.isEmpty() ? null : rides.get(rides.size() - 1).routeNo();
      StopPair pair = stopRoute == null ? null
          : stopPairs.get(stopKey(fromPoi == null ? terminal : fromPoi, toPoi == null ? terminal : toPoi, stopRoute));
      // 내리는 곳은 가는 곳이 스팟일 때만, 타는 곳은 출발 쪽이 스팟일 때만 — 고현터미널은 그 자체가 정류장이라 0m 다.
      StopWalk alight = pair == null || l.get("to_poi_id") == null ? null : pair.alight();
      StopWalk board = pair == null || l.get("from_poi_id") == null ? null : pair.board();
      // 배 구간에만 배편이 붙는다(V36 의 chk_leg_ferry_course 가 그것을 지킨다).
      // 값은 ferry_courses 원문 그대로다 — 여기서 계산하는 것이 하나도 없다.
      FerryRide ferry = l.get("legend_label") == null ? null
          : new FerryRide((String) l.get("legend_label"), (String) l.get("ferry_course_name"),
              (String) l.get("total_text"), (Integer) l.get("oedo_stay_min"),
              Boolean.TRUE.equals(l.get("lands_on_oedo")), (String) l.get("dock_name"),
              (String) l.get("booking_url"));
      legs.add(new Leg(
          (int) num(l.get("leg_seq")), mode,
          fromPoi, fromPoi == null ? ORIGIN_NAME : (String) l.get("from_name"),
          toPoi, toPoi == null ? ORIGIN_NAME : (String) l.get("to_name"),
          hm(l.get("depart_time")), hm(l.get("arrive_time")),
          (int) num(l.get("duration_min")), (int) num(l.get("transfers")),
          (int) num(l.get("transfer_wait_min")), est, rides, board, alight, ferry,
          service, calc == null ? null : calc.holidayNoBus()));
    }

    Integer approx = (Integer) c.get("approx_total_min");
    // 구간 이동시간 합. 이미 만든 legs 를 더하므로 쿼리를 더 쏘지 않는다.
    // ★ 버스 분은 구간 줄이 적는 분(대표 노선의 늦게 닿는 분)의 합이다 — 카드(legSumsByCourse)와 같은 규칙.
    // 배 · 같은 정류장 구간은 버스가 아니라 세지 않는다.
    int busMin = legs.stream().filter(x -> "BUS".equals(x.mode()))
        .mapToInt(x -> x.service() != null ? x.service().durationMin() : x.durationMin()).sum();
    int ferryMin = legs.stream().filter(x -> "FERRY".equals(x.mode())).mapToInt(Leg::durationMin).sum();
    var noBusLegs = legs.stream().filter(x -> Boolean.TRUE.equals(x.holidayNoBus()))
        .map(x -> new LegName(x.fromName(), x.toName())).toList();
    return new CourseDetail(courseId, (String) c.get("course_code"),
        (String) c.get("course_name"), (String) c.get("summary"),
        (String) c.get("title"), (String) c.get("intro"),
        (Integer) c.get("featured_rank"), (String) c.get("badge_axis"), officialCourse(c),
        (String) c.get("theme"),
        (Integer) c.get("spot_count"), (Integer) c.get("nine_scenic_count"),
        hm(c.get("depart_time")), hm(c.get("return_time")),
        (Integer) c.get("total_min"), approx,
        approx == null ? null : approxText(approx),
        busMin, approxText(busMin),
        ferryMin, ferryMin == 0 ? null : approxText(ferryMin),
        noBusLegs,
        legs.size(), (int) legs.stream().filter(Leg::estimated).count(),
        (String) c.get("service"),
        c.get("base_date") == null ? null : c.get("base_date").toString(),
        c.get("course_code") == null ? null : SOURCE,
        c.get("course_code") == null ? null : ORIGIN_NAME,
        (String) c.get("origin_stop"),
        stops, legs);
  }

  // 코스 존재 확인(hasCourse)은 SavedTripController가 DB에서 직접 한다 —
  // 코스가 상수에서 DB로 옮겨왔고, 상수만 보면 추천 코스 저장이 전부 400이 된다.

  // ── 표시 형식 ─────────────────────────────────────────────────────────────

  /**
   * "약 8시간 30분" / "약 10시간". 정시간이면 분을 붙이지 않는다 —
   * "약 10시간 0분"은 사람이 쓰는 말이 아니다.
   */
  static String approxText(int min) {
    int h = min / 60;
    int m = min % 60;
    return m == 0 ? "약 " + h + "시간" : "약 " + h + "시간 " + m + "분";
  }

  private static String hm(Object time) {
    return time == null ? null : ((java.sql.Time) time).toLocalTime().format(HM);
  }

  /** time 컬럼 → 자정 기준 분. 시간표 엔진이 쓰는 단위다(engine.md). */
  private static int minutes(Object time) {
    LocalTime t = ((java.sql.Time) time).toLocalTime();
    return t.getHour() * 60 + t.getMinute();
  }

  private static long num(Object o) {
    return ((Number) o).longValue();
  }

  private static Double dbl(Object o) {
    return o == null ? null : ((Number) o).doubleValue();
  }
}
