-- V19 (2026-09-13): 스팟 시간표를 추천 코스와 같은 원천으로 — 급행 2000번 · 스팟 정류장 이름
--
-- 왜 필요한가
--   스팟 시간표가 원문 격자만 읽어서, 격자에 칸이 없는 스팟 정류장이 전부 빈손이었다.
--   김영삼 생가(대계)·매미성(대금교차로)·포로수용소·거제식물원·옥포대첩기념공원·
--   맹종죽테마공원은 "정류장 칸 없음", 바람의언덕(도장포)은 "운행 없음" — 그런데 추천 코스는
--   같은 구간을 "55번 · 12분"으로 적는다. 코스와 시간표가 서로 다른 말을 했다.
--   팀원 파이프라인(geojero-client/timetable/build_spot_times.py)이 이 빈칸을 사람이 확인한
--   규칙으로 메웠고 코스는 그 결과로 계산됐다. 규칙은 코드(engine/SpotLayer)로 옮겼고,
--   여기서는 **데이터로만 줄 수 있는 두 가지**를 넣는다.
--
-- 1) 급행 2000번 — 원문 시트 「2000번(거제고현↔부산하단)-」에는 고현 출발 시각만 있다. 대계 도착은
--    고현 출발 + 60분(사용자 제공 소요시간)이다. 원문 시각이 아니므로 time_kind = ANNOTATION 이고
--    스팟 계층이 이 도착을 추정(estimated)으로 표시한다. 대계 → 고현(부산발)은 부산 하단 → 대계
--    소요시간을 몰라 넣지 않는다. 요일 구분이 없는 시트라 평일·휴일 모두 운행으로 둔다.
--    출발 39회 — spot_times.json 의 GOHYEON→DAEGYE 2000 구간과 같다(원본 xlsx 에서 다시 뽑아 대조).
-- 2) pois.timetable_stop — V18 이 NULL 로 둔 스팟에 스팟 계층의 정류장 이름을 준다.
--    이름이 스팟 계층(SpotLayer.VIRTUAL_STOPS · 경로 문장의 지명)과 글자까지 같아야 조회된다.
--
-- 원문 조회(/api/stops)와 회귀 게이트는 격자를 그대로 읽는다 — 2000번 회차가 늘어난 것 말고는
-- 격자 사실이 바뀌지 않는다(대계는 기존 회차 어디에도 칸이 없던 새 정류장이다).

INSERT INTO stops (stop_name) VALUES ('대계') ON CONFLICT (stop_name) DO NOTHING;
INSERT INTO routes (route_no, route_name, route_type)
VALUES ('2000', '급행 2000번 (거제고현↔부산하단)', 'INTERCITY')
ON CONFLICT (route_no, route_type) DO NOTHING;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 5
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 360, '06:00', 'LITERAL', 'B5'),
  (2, '대계', 420, '대계 도착 07:00 = 고현 06:00 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B5')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 6
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 380, '06:20', 'LITERAL', 'B6'),
  (2, '대계', 440, '대계 도착 07:20 = 고현 06:20 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B6')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 7
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 400, '06:40', 'LITERAL', 'B7'),
  (2, '대계', 460, '대계 도착 07:40 = 고현 06:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B7')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 8
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 420, '07:00', 'LITERAL', 'B8'),
  (2, '대계', 480, '대계 도착 08:00 = 고현 07:00 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B8')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 9
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 440, '07:20', 'LITERAL', 'B9'),
  (2, '대계', 500, '대계 도착 08:20 = 고현 07:20 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B9')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 10
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 460, '07:40', 'LITERAL', 'B10'),
  (2, '대계', 520, '대계 도착 08:40 = 고현 07:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B10')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 11
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 490, '08:10', 'LITERAL', 'B11'),
  (2, '대계', 550, '대계 도착 09:10 = 고현 08:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B11')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 12
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 520, '08:40', 'LITERAL', 'B12'),
  (2, '대계', 580, '대계 도착 09:40 = 고현 08:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B12')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 13
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 550, '09:10', 'LITERAL', 'B13'),
  (2, '대계', 610, '대계 도착 10:10 = 고현 09:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B13')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 14
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 580, '09:40', 'LITERAL', 'B14'),
  (2, '대계', 640, '대계 도착 10:40 = 고현 09:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B14')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 15
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 610, '10:10', 'LITERAL', 'B15'),
  (2, '대계', 670, '대계 도착 11:10 = 고현 10:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B15')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 16
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 640, '10:40', 'LITERAL', 'B16'),
  (2, '대계', 700, '대계 도착 11:40 = 고현 10:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B16')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 17
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 670, '11:10', 'LITERAL', 'B17'),
  (2, '대계', 730, '대계 도착 12:10 = 고현 11:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B17')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 18
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 700, '11:40', 'LITERAL', 'B18'),
  (2, '대계', 760, '대계 도착 12:40 = 고현 11:40 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B18')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 19
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 730, '12:10', 'LITERAL', 'B19'),
  (2, '대계', 790, '대계 도착 13:10 = 고현 12:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B19')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 20
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 755, '12:35', 'LITERAL', 'B20'),
  (2, '대계', 815, '대계 도착 13:35 = 고현 12:35 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B20')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 21
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 785, '13:05', 'LITERAL', 'B21'),
  (2, '대계', 845, '대계 도착 14:05 = 고현 13:05 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B21')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 22
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 815, '13:35', 'LITERAL', 'B22'),
  (2, '대계', 875, '대계 도착 14:35 = 고현 13:35 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B22')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 23
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 845, '14:05', 'LITERAL', 'B23'),
  (2, '대계', 905, '대계 도착 15:05 = 고현 14:05 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B23')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 24
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 870, '14:30', 'LITERAL', 'B24'),
  (2, '대계', 930, '대계 도착 15:30 = 고현 14:30 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B24')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 25
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 900, '15:00', 'LITERAL', 'B25'),
  (2, '대계', 960, '대계 도착 16:00 = 고현 15:00 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B25')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 26
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 930, '15:30', 'LITERAL', 'B26'),
  (2, '대계', 990, '대계 도착 16:30 = 고현 15:30 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B26')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 27
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 955, '15:55', 'LITERAL', 'B27'),
  (2, '대계', 1015, '대계 도착 16:55 = 고현 15:55 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B27')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 28
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 980, '16:20', 'LITERAL', 'B28'),
  (2, '대계', 1040, '대계 도착 17:20 = 고현 16:20 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B28')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 29
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1005, '16:45', 'LITERAL', 'B29'),
  (2, '대계', 1065, '대계 도착 17:45 = 고현 16:45 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B29')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 30
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1030, '17:10', 'LITERAL', 'B30'),
  (2, '대계', 1090, '대계 도착 18:10 = 고현 17:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B30')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 31
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1055, '17:35', 'LITERAL', 'B31'),
  (2, '대계', 1115, '대계 도착 18:35 = 고현 17:35 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B31')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 32
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1080, '18:00', 'LITERAL', 'B32'),
  (2, '대계', 1140, '대계 도착 19:00 = 고현 18:00 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B32')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 33
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1105, '18:25', 'LITERAL', 'B33'),
  (2, '대계', 1165, '대계 도착 19:25 = 고현 18:25 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B33')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 34
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1135, '18:55', 'LITERAL', 'B34'),
  (2, '대계', 1195, '대계 도착 19:55 = 고현 18:55 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B34')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 35
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1165, '19:25', 'LITERAL', 'B35'),
  (2, '대계', 1225, '대계 도착 20:25 = 고현 19:25 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B35')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 36
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1195, '19:55', 'LITERAL', 'B36'),
  (2, '대계', 1255, '대계 도착 20:55 = 고현 19:55 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B36')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 37
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1220, '20:20', 'LITERAL', 'B37'),
  (2, '대계', 1280, '대계 도착 21:20 = 고현 20:20 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B37')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 38
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1250, '20:50', 'LITERAL', 'B38'),
  (2, '대계', 1310, '대계 도착 21:50 = 고현 20:50 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B38')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 39
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1270, '21:10', 'LITERAL', 'B39'),
  (2, '대계', 1330, '대계 도착 22:10 = 고현 21:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B39')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 40
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1290, '21:30', 'LITERAL', 'B40'),
  (2, '대계', 1350, '대계 도착 22:30 = 고현 21:30 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B40')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 41
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1310, '21:50', 'LITERAL', 'B41'),
  (2, '대계', 1370, '대계 도착 22:50 = 고현 21:50 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B41')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 42
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1330, '22:10', 'LITERAL', 'B42'),
  (2, '대계', 1390, '대계 도착 23:10 = 고현 22:10 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B42')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;
WITH t AS (
  INSERT INTO trips (version_id, route_id, direction, runs_weekday, runs_holiday, headsign_raw, note_raw, source_sheet, source_row)
  SELECT v.version_id, r.route_id, 0, true, true, '부산하단', '대계 도착은 고현 출발 + 60분(사용자 제공)', '2000번(거제고현↔부산하단)-', 43
  FROM timetable_versions v, routes r
  WHERE v.source_file = '3000번4000번.xlsx' AND v.valid_to IS NULL AND r.route_no = '2000' AND r.route_type = 'INTERCITY'
  RETURNING trip_id)
INSERT INTO trip_stops (trip_id, seq, stop_id, status, depart_min, raw_text, time_kind, source_cell)
SELECT t.trip_id, x.seq, s.stop_id, 'TIME'::stop_status, x.m, x.raw, x.kind::time_kind, x.cell
FROM t CROSS JOIN (VALUES
  (1, '고현', 1350, '22:30', 'LITERAL', 'B43'),
  (2, '대계', 1410, '대계 도착 23:30 = 고현 22:30 + 60분(사용자 제공 · 원문에 대계 시각 없음)', 'ANNOTATION', 'B43')) AS x(seq, stop, m, raw, kind, cell)
JOIN stops s ON s.stop_name = x.stop;

-- 스팟 정류장 이름(스팟 계층과 같은 글자). alight_label 은 V18 그대로다.
UPDATE pois SET timetable_stop = '대계'             WHERE poi_id = 22;  -- 김영삼 전 대통령 생가: 32·34·2000번
UPDATE pois SET timetable_stop = '대금교차로'       WHERE poi_id = 7;   -- 매미성: 외포 바로 앞(장목·두모실 쪽)
UPDATE pois SET timetable_stop = '포로수용소'       WHERE poi_id = 13;  -- 100·110번 백병원-포로수용소-…-시청
UPDATE pois SET timetable_stop = '식물원'           WHERE poi_id = 10;  -- 50-2번 거제면사무소-식물원-외간교회(종점)
UPDATE pois SET timetable_stop = '옥포대첩기념공원' WHERE poi_id = 21;  -- 덕포와 중앙시장 사이
UPDATE pois SET timetable_stop = '맹종죽테마파크'   WHERE poi_id = 17;  -- 하청 다음 거제북로 위
