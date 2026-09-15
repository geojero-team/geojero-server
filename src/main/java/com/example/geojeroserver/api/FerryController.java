package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.FerryTable;
import com.example.geojeroserver.engine.TimeUtil;
import com.example.geojeroserver.exception.BusinessException;
import com.example.geojeroserver.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 유람선 시간표 — 스팟 시간표 화면의 배 칩(2026-09-14 사용자 결정 · Figma 프레임 없음).
 *
 * 버스 시간표(`/api/pois/{id}/departures`)와 **따로 둔다**. 배는 요일 구분이 없고 날짜마다 원문이 있으며
 * 왕복이다. 한 응답에 칩에 필요한 선착장을 전부 담아, 칩을 바꿀 때 다시 부르지 않게 한다.
 *
 * 어떤 배를 주는가(ferry_links — 상품 페이지에 근거 문장이 있는 연결만):
 *   DESTINATION  외도보타니아 화면 → 선착장마다 하나, **외도상륙 편만**(선상관광은 외도에 내리지 않는다)
 *   DOCK         도장포유람선 화면 → 그 선착장의 두 코스 모두
 *   TOWARD       다음 스팟이 외도이고, 이 스팟이 선착장이거나 선착장 근처라는 원문이 있을 때 → 외도상륙 편만
 *   다음 스팟이 외도인데 근처 선착장 원문이 없으면 비우고 towardEmptyReason 을 준다. 추측으로 선착장을 잇지 않는다.
 */
@RestController
public class FerryController {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
  private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
  private static final Pattern DAYS = Pattern.compile("^\\d{1,3}$");
  private static final int DEFAULT_DAYS = 14;
  private static final int MAX_DAYS = 62;
  private static final String SOURCE = "외도유람선 예약센터";

  public record AsOf(String date, String time, String zone) {}

  public record Dock(String dockCode, String operatorName, String shortName, String address) {}

  public record Access(String quote, String sourceUrl) {}

  public record Course(long courseId, boolean landsOnOedo, String legendLabel, String name,
      int totalMin, String totalText, Integer oedoStayMin, String bookingUrl) {}

  public record SailingItem(String depart, long courseId, String returnApprox) {}

  public record Row(String date, String status, List<SailingItem> sailings) {}

  public record NextItem(long courseId, String date, String depart, String returnApprox) {}

  public record CoverageInfo(String publishedThrough, String fetchedAt, String source,
      String sourceUrl, String crossCheckUrl) {}

  public record Ferry(String key, String relation, boolean landingOnly, Dock dock, Access access,
      List<Course> courses, List<NextItem> next, List<Row> rows, CoverageInfo coverage) {}

  /**
   * 도선(V31, 2026-09-15) — 섬으로 들어가는 배(inTimes, 선착장 → 섬) · 나오는 배(outTimes, 섬 → 선착장).
   * 요청 날짜의 평일/휴일(dayClass)에 맞는 시각만 싣는다. 그날 정해진 시각이 없으면 두 목록이 비고
   * holidayNote(원문 「5번~8번 (주말 수시운행)」)가 이유다. holidayNote 는 평일에도 실어 「주말은 이렇다」를 알린다.
   */
  public record Shuttle(long shuttleId, String dockName, String islandName, String operatorName,
      String address, String phone, String tripNote, String fareText, String bookingUrl, String notice,
      String dayClass, List<String> inTimes, List<String> outTimes, String holidayNote,
      String source, String enteredOn) {}

  public record FerriesRes(long poiId, String shortName, boolean hasBusStop, Long toPoiId,
      boolean toIsFerryDestination, String towardEmptyReason, AsOf asOf, int days, List<Ferry> ferries,
      List<Shuttle> shuttles) {}

  private record Link(int dockId, String relation, String quote, String sourceUrl, int seq) {}

  private final JdbcTemplate jdbc;
  // 도선 시각의 평일/휴일 — 버스와 같은 판정을 쓴다(주말 · 공휴일 HOLIDAY). 한 화면의 「평일」 알약이 버스와 배에서 달라지면 안 된다.
  private final SnapshotService snapshots;

  public FerryController(JdbcTemplate jdbc, SnapshotService snapshots) {
    this.jdbc = jdbc;
    this.snapshots = snapshots;
  }

  @GetMapping("/api/pois/{poiId}/ferries")
  public FerriesRes ferries(@PathVariable long poiId,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Long toPoiId,
      @RequestParam(required = false) String days) {

    // 형식이 틀리면 400 — 버스 엔드포인트처럼 파싱 예외가 500 으로 새지 않게 직접 읽는다.
    LocalDate today = LocalDate.now(KST);
    LocalDate start = parseDate(date, today);
    if (after != null && !HHMM.matcher(after).matches()) throw invalid();
    int window = parseDays(days);
    // after 를 주지 않았는데 오늘을 물으면 지금 시각부터, 다른 날을 물으면 그날 첫 편부터 다음 배다.
    String asOfTime = after != null ? after
        : start.equals(today) ? LocalTime.now(KST).format(DateTimeFormatter.ofPattern("HH:mm")) : null;

    var spot = poi(poiId);
    if (toPoiId != null) poi(toPoiId);

    var own = links(poiId);
    var toward = toPoiId == null ? List.<Link>of() : links(toPoiId).stream()
        .filter(l -> l.relation().equals("DESTINATION")).toList();
    boolean toIsDestination = !toward.isEmpty();

    var out = new ArrayList<Ferry>();
    Integer asOfMin = asOfTime == null ? null : TimeUtil.hhmmToMin(asOfTime);
    for (var l : own) {
      if (l.relation().equals("DESTINATION")) {
        out.add(ferry("DESTINATION", l, true, null, start, window, start, asOfMin));
      } else if (l.relation().equals("DOCK")) {
        out.add(ferry("DOCK", l, false, null, start, window, start, asOfMin));
      }
    }
    boolean ownIsDestination = own.stream().anyMatch(l -> l.relation().equals("DESTINATION"));
    String towardEmpty = null;
    if (toIsDestination && !ownIsDestination) {
      var towardDocks = toward.stream().map(Link::dockId).toList();
      var near = own.stream()
          .filter(l -> (l.relation().equals("DOCK") || l.relation().equals("NEAR_DOCK"))
              && towardDocks.contains(l.dockId()))
          .toList();
      for (var l : near) {
        var access = l.relation().equals("NEAR_DOCK") ? new Access(l.quote(), l.sourceUrl()) : null;
        out.add(ferry("TOWARD", l, true, access, start, window, start, asOfMin));
      }
      if (near.isEmpty()) towardEmpty = "NO_DOCK_NEAR_SPOT";
    }

    return new FerriesRes(poiId, (String) spot.get("short_name"), spot.get("timetable_stop") != null,
        toPoiId, toIsDestination, towardEmpty, new AsOf(start.toString(), asOfTime, KST.getId()),
        window, out, shuttles(poiId, start));
  }

  /**
   * 도선 — 이 스팟으로 가는 선착장마다 하나(V31 shuttle_docks). 외도 유람선과 달리 날짜별 원문이 아니라
   * 요일별 고정 시각이라, 요청한 날짜의 평일/휴일에 맞는 행(ALL + 그 날의 day_type)만 준다.
   */
  private List<Shuttle> shuttles(long poiId, LocalDate date) {
    var docks = jdbc.queryForList("""
        SELECT shuttle_id, dock_name, island_name, operator_name, address, phone, trip_note,
               fare_text, booking_url, notice, holiday_note, source, entered_on
        FROM shuttle_docks WHERE poi_id = ? ORDER BY shuttle_id""", poiId);
    if (docks.isEmpty()) return List.of();
    String dayClass = snapshots.forDate(date.toString()).dayClass().name();
    var out = new ArrayList<Shuttle>();
    for (var d : docks) {
      long id = ((Number) d.get("shuttle_id")).longValue();
      var in = new ArrayList<String>();
      var back = new ArrayList<String>();
      jdbc.query("""
          SELECT direction, to_char(depart_time, 'HH24:MI') AS t FROM shuttle_departures
          WHERE shuttle_id = ? AND day_type IN ('ALL', ?) ORDER BY depart_time""",
          (org.springframework.jdbc.core.RowCallbackHandler) rs ->
              ("IN".equals(rs.getString("direction")) ? in : back).add(rs.getString("t")),
          id, dayClass);
      out.add(new Shuttle(id, (String) d.get("dock_name"), (String) d.get("island_name"),
          (String) d.get("operator_name"), (String) d.get("address"), (String) d.get("phone"),
          (String) d.get("trip_note"), (String) d.get("fare_text"), (String) d.get("booking_url"),
          (String) d.get("notice"), dayClass, List.copyOf(in), List.copyOf(back),
          (String) d.get("holiday_note"), (String) d.get("source"), d.get("entered_on").toString()));
    }
    return out;
  }

  private Ferry ferry(String relation, Link link, boolean landingOnly, Access access,
      LocalDate start, int window, LocalDate asOfDate, Integer asOfMin) {
    var dock = jdbc.queryForObject("""
        SELECT dock_code, operator_name, short_name, address FROM ferry_docks WHERE dock_id = ?""",
        (rs, i) -> new Dock(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), link.dockId());

    var courses = jdbc.query("""
        SELECT course_id, lands_on_oedo, legend_label, course_name, total_min, total_text, oedo_stay_min, booking_url
        FROM ferry_courses WHERE dock_id = ? AND (lands_on_oedo OR NOT ?)
        ORDER BY lands_on_oedo DESC, course_id""",
        (rs, i) -> new Course(rs.getLong(1), rs.getBoolean(2), rs.getString(3), rs.getString(4),
            rs.getInt(5), rs.getString(6), (Integer) rs.getObject(7), rs.getString(8)),
        link.dockId(), landingOnly);
    var engineCourses = new LinkedHashMap<Long, FerryTable.Course>();
    for (var c : courses) engineCourses.put(c.courseId(), new FerryTable.Course(c.courseId(), c.landsOnOedo(), c.totalMin()));

    var coverages = jdbc.query("""
        SELECT coverage_id, covered_from, covered_to, fetched_at, source_url, cross_check_url, month
        FROM ferry_coverage WHERE dock_id = ? ORDER BY fetched_at, month""",
        (rs, i) -> Map.<String, Object>of(
            "c", new FerryTable.Coverage(rs.getLong(1), rs.getObject(7, LocalDate.class),
                rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class),
                rs.getObject(4, OffsetDateTime.class),
                rs.getObject(4, OffsetDateTime.class).atZoneSameInstant(KST).toLocalDate()),
            "src", rs.getString(5),
            "xcheck", rs.getString(6) == null ? "" : rs.getString(6)),
        link.dockId());
    var engineCoverages = coverages.stream().map(m -> (FerryTable.Coverage) m.get("c")).toList();

    LocalDate end = start.plusDays(window - 1);
    var sailings = jdbc.query("""
        SELECT s.coverage_id, s.course_id, s.sail_date, s.depart_time
        FROM ferry_sailings s JOIN ferry_coverage c USING (coverage_id)
        WHERE c.dock_id = ? AND s.sail_date BETWEEN ? AND ?""",
        (rs, i) -> new FerryTable.Sailing(rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate.class),
            TimeUtil.hhmmToMin(rs.getObject(4, LocalTime.class).format(DateTimeFormatter.ofPattern("HH:mm")))),
        link.dockId(), start, end);

    var r = FerryTable.build(engineCoverages, sailings, engineCourses, start, window, asOfDate, asOfMin);

    var rows = r.rows().stream().map(d -> new Row(d.date().toString(), d.status().name(),
        d.sailings().stream().map(s -> new SailingItem(TimeUtil.minToHHMM(s.departMin()), s.courseId(),
            TimeUtil.minToHHMM(s.returnMin()))).toList())).toList();
    var next = r.next().stream().map(n -> new NextItem(n.courseId(), n.date().toString(),
        TimeUtil.minToHHMM(n.departMin()), TimeUtil.minToHHMM(n.returnMin()))).toList();

    CoverageInfo info = null;
    if (!coverages.isEmpty()) {
      var latest = coverages.get(coverages.size() - 1);
      var through = engineCoverages.stream().map(FerryTable.Coverage::to).max(LocalDate::compareTo).get();
      var fetched = ((FerryTable.Coverage) latest.get("c")).fetchedAt().withOffsetSameInstant(KST_OFFSET);
      String xcheck = (String) latest.get("xcheck");
      info = new CoverageInfo(through.toString(), fetched.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
          SOURCE, (String) latest.get("src"), xcheck.isEmpty() ? null : xcheck);
    }
    return new Ferry(relation + ":" + dock.dockCode(), relation, landingOnly, dock, access,
        courses, next, rows, info);
  }

  // ── 조회 ─────────────────────────────────────────────────────────────────

  private List<Link> links(long poiId) {
    return jdbc.query("""
        SELECT l.dock_id, l.relation, l.source_quote, l.source_url, d.seq
        FROM ferry_links l JOIN ferry_docks d USING (dock_id)
        WHERE l.poi_id = ? ORDER BY d.seq""",
        (rs, i) -> new Link(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)),
        poiId);
  }

  private Map<String, Object> poi(long poiId) {
    var rows = jdbc.queryForList("SELECT poi_id, short_name, timetable_stop FROM pois WHERE poi_id = ?", poiId);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 스팟");
    return rows.get(0);
  }

  private static LocalDate parseDate(String date, LocalDate today) {
    if (date == null) return today;
    try {
      return LocalDate.parse(date);
    } catch (DateTimeParseException e) {
      throw invalid();
    }
  }

  private static int parseDays(String days) {
    if (days == null) return DEFAULT_DAYS;
    if (!DAYS.matcher(days).matches()) throw invalid();
    int n = Integer.parseInt(days);
    if (n < 1 || n > MAX_DAYS) throw invalid();
    return n;
  }

  private static BusinessException invalid() {
    return new BusinessException(ErrorCode.VALIDATION_FAILED);
  }
}
