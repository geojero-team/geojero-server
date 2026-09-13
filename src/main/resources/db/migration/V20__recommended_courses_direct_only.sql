-- V20 (2026-09-13): 추천 코스 재적재 — 직행만 · 섬 코스 제외 · 23개 (3곳 10 · 4곳 10 · 5곳 3)
--
-- 출처: 팀원(김도현) 산출물 recommended_courses.json (recommended_courses.txt 확정본, 계산 2026-09-13)
--       근거 시간표는 거제시 BIS 원문 2026-08-18, 평일 기준.
--
-- V17 과 무엇이 다른가
--   V17 은 환승이 섞였던 옛 30개에서 직행·섬 없는 3개만 골라 넣었다. 팀원이 **직행만으로 다시
--   계산**했고(모든 구간 버스 한 대), 배 시각이 없는 섬(동백섬 지심도·내도)과 서버에 스팟이 없는
--   공곶이를 후보에서 뺀 뒤 같은 순위 규칙으로 스팟 수별 10개까지 골랐다(5곳은 가능한 3개 전부).
--   환승 0 · 모든 BUS 구간 버스 1대 — CourseDataTest 가 지킨다.
--
-- course_id 유지
--   V17 의 101(3-01)·102(5-01)·103(5-07)은 새 목록에 **같은 코스**가 있어 id 를 그대로 쓴다
--   (저장 일정 saved_trips.course_id 가 가리킬 수 있어 지우지 않는다). 103 은 순위가 바뀌어 코드가
--   5-03 가 된다. 나머지는 104 부터 새로 넣는다.
--
-- 추정 시각(board/alight_estimated)은 시각 칸이 없는 정류장(도장포·대금교차로·맹종죽테마파크)에만
-- 붙는다 — 스팟 계층(engine/SpotLayer)이 같은 규칙으로 같은 값을 낸다(CourseTimetableConsistencyTest).

DELETE FROM course_legs WHERE course_id IN (101, 102, 103);   -- course_rides 는 ON DELETE CASCADE
DELETE FROM course_pois WHERE course_id IN (101, 102, 103);

-- ── 3-01 (3곳 · 9경 3곳) : 학동 · 해금강 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (101, '학동 · 해금강 · 바람의언덕', '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-01', 3, 1,
        3, '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 4, 1, true, '11:45', '13:45', 120);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 3, 2, true, '13:55', '16:38', 163);   -- 해금강
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 1, 3, true, '16:50', '18:48', 118);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (101, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false FROM course_legs WHERE course_id = 101 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (101, 2, 4, 3, 'BUS', '13:45', '13:55', 10, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '13:45', false, '해금강', '13:55', false FROM course_legs WHERE course_id = 101 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (101, 3, 3, 1, 'BUS', '16:38', '16:50', 12, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '해금강', '16:38', false, '도장포', '16:50', true FROM course_legs WHERE course_id = 101 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (101, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false FROM course_legs WHERE course_id = 101 AND leg_seq = 4;

-- ── 3-02 (3곳 · 9경 2곳) : 씨월드 · 학동 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (104, '씨월드 · 학동 · 바람의언덕', '고현터미널 11:04 출발 → 20:55 복귀 · 약 10시간', NULL, '3-02', 3, 2,
        2, '11:04', '20:55', 591, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (104, 18, 1, true, '11:48', '15:00', 192);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (104, 4, 2, true, '15:30', '17:45', 135);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (104, 1, 3, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (104, 1, NULL, 18, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 104 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (104, 2, 18, 4, 'BUS', '15:00', '15:30', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '학동', '15:30', false FROM course_legs WHERE course_id = 104 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (104, 3, 4, 1, 'BUS', '17:45', '17:55', 10, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '17:45', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 104 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (104, 4, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 104 AND leg_seq = 4;

-- ── 3-03 (3곳 · 9경 2곳) : 해금강 · 학동 · 양지암조각공원
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (105, '해금강 · 학동 · 양지암조각공원', '고현터미널 11:05 출발 → 19:35 복귀 · 약 8시간 30분', NULL, '3-03', 3, 3,
        2, '11:05', '19:35', 510, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (105, 3, 1, true, '11:55', '14:48', 173);   -- 해금강
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (105, 4, 2, true, '15:00', '17:08', 128);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (105, 16, 3, true, '17:56', '18:56', 60);   -- 양지암조각공원
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (105, 1, NULL, 3, 'BUS', '11:05', '11:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '해금강', '11:55', false FROM course_legs WHERE course_id = 105 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (105, 2, 3, 4, 'BUS', '14:48', '15:00', 12, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '해금강', '14:48', false, '학동', '15:00', false FROM course_legs WHERE course_id = 105 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (105, 3, 4, 16, 'BUS', '17:08', '17:56', 48, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '학동', '17:08', false, '능포', '17:56', false FROM course_legs WHERE course_id = 105 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (105, 4, 16, NULL, 'BUS', '18:56', '19:35', 39, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '능포', '18:56', false, '고현', '19:35', false FROM course_legs WHERE course_id = 105 AND leg_seq = 4;

-- ── 3-04 (3곳 · 9경 2곳) : 해금강 · 기성관 · 학동
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (106, '해금강 · 기성관 · 학동', '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-04', 3, 4,
        2, '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (106, 3, 1, true, '11:55', '14:48', 173);   -- 해금강
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (106, 19, 2, true, '15:20', '16:46', 86);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (106, 4, 3, true, '17:08', '19:00', 112);   -- 학동
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (106, 1, NULL, 3, 'BUS', '11:05', '11:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '해금강', '11:55', false FROM course_legs WHERE course_id = 106 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (106, 2, 3, 19, 'BUS', '14:48', '15:20', 32, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '해금강', '14:48', false, '거제', '15:20', false FROM course_legs WHERE course_id = 106 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (106, 3, 19, 4, 'BUS', '16:46', '17:08', 22, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '거제', '16:46', false, '학동', '17:08', false FROM course_legs WHERE course_id = 106 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (106, 4, 4, NULL, 'BUS', '19:00', '19:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '19:00', false, '고현', '19:40', false FROM course_legs WHERE course_id = 106 AND leg_seq = 4;

-- ── 3-05 (3곳 · 9경 2곳) : 학동 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (107, '학동 · 기성관 · 바람의언덕', '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-05', 3, 5,
        2, '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (107, 4, 1, true, '11:45', '13:00', 75);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (107, 19, 2, true, '13:20', '15:25', 125);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (107, 1, 3, true, '16:10', '18:48', 158);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (107, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false FROM course_legs WHERE course_id = 107 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (107, 2, 4, 19, 'BUS', '13:00', '13:20', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '13:00', false, '거제', '13:20', false FROM course_legs WHERE course_id = 107 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (107, 3, 19, 1, 'BUS', '15:25', '16:10', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55-1', '거제', '15:25', false, '도장포', '16:10', true FROM course_legs WHERE course_id = 107 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (107, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false FROM course_legs WHERE course_id = 107 AND leg_seq = 4;

-- ── 3-06 (3곳 · 9경 2곳) : 기성관 · 해금강 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (108, '기성관 · 해금강 · 바람의언덕', '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-06', 3, 6,
        2, '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (108, 19, 1, true, '11:25', '13:25', 120);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (108, 3, 2, true, '13:55', '16:38', 163);   -- 해금강
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (108, 1, 3, true, '16:50', '18:48', 118);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (108, 1, NULL, 19, 'BUS', '11:05', '11:25', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '거제', '11:25', false FROM course_legs WHERE course_id = 108 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (108, 2, 19, 3, 'BUS', '13:25', '13:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '13:25', false, '해금강', '13:55', false FROM course_legs WHERE course_id = 108 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (108, 3, 3, 1, 'BUS', '16:38', '16:50', 12, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '해금강', '16:38', false, '도장포', '16:50', true FROM course_legs WHERE course_id = 108 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (108, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false FROM course_legs WHERE course_id = 108 AND leg_seq = 4;

-- ── 3-07 (3곳 · 9경 1곳) : 학동 · 씨월드 · 양지암조각공원
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (109, '학동 · 씨월드 · 양지암조각공원', '고현터미널 11:05 출발 → 17:03 복귀 · 약 6시간', NULL, '3-07', 3, 7,
        1, '11:05', '17:03', 358, 360,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (109, 4, 1, true, '11:45', '13:10', 85);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (109, 18, 2, true, '13:37', '14:57', 80);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (109, 16, 3, true, '15:18', '16:24', 66);   -- 양지암조각공원
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (109, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false FROM course_legs WHERE course_id = 109 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (109, 2, 4, 18, 'BUS', '13:10', '13:37', 27, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '학동', '13:10', false, '지세포', '13:37', false FROM course_legs WHERE course_id = 109 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (109, 3, 18, 16, 'BUS', '14:57', '15:18', 21, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '61', '지세포', '14:57', false, '능포', '15:18', false FROM course_legs WHERE course_id = 109 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (109, 4, 16, NULL, 'BUS', '16:24', '17:03', 39, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '11', '능포', '16:24', false, '고현', '17:03', false FROM course_legs WHERE course_id = 109 AND leg_seq = 4;

-- ── 3-08 (3곳 · 9경 1곳) : 학동 · 조선해양문화관 · 씨월드
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (110, '학동 · 조선해양문화관 · 씨월드', '고현터미널 11:05 출발 → 16:50 복귀 · 약 6시간', NULL, '3-08', 3, 8,
        1, '11:05', '16:50', 345, 360,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (110, 4, 1, true, '11:45', '13:10', 85);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (110, 20, 2, true, '13:37', '14:37', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (110, 18, 3, true, '14:37', '16:05', 88);   -- 씨월드
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (110, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false FROM course_legs WHERE course_id = 110 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (110, 2, 4, 20, 'BUS', '13:10', '13:37', 27, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '학동', '13:10', false, '지세포', '13:37', false FROM course_legs WHERE course_id = 110 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (110, 3, 20, 18, 'SAME_STOP', '14:37', '14:37', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (110, 4, 18, NULL, 'BUS', '16:05', '16:50', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22', '지세포', '16:05', false, '고현', '16:50', false FROM course_legs WHERE course_id = 110 AND leg_seq = 4;

-- ── 3-09 (3곳 · 9경 1곳) : 씨월드 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (111, '씨월드 · 기성관 · 바람의언덕', '고현터미널 11:04 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-09', 3, 9,
        1, '11:04', '19:40', 516, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (111, 18, 1, true, '11:48', '13:00', 72);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (111, 19, 2, true, '13:50', '15:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (111, 1, 3, true, '16:10', '18:48', 158);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (111, 1, NULL, 18, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 111 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (111, 2, 18, 19, 'BUS', '13:00', '13:50', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '13:00', false, '거제', '13:50', false FROM course_legs WHERE course_id = 111 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (111, 3, 19, 1, 'BUS', '15:25', '16:10', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55-1', '거제', '15:25', false, '도장포', '16:10', true FROM course_legs WHERE course_id = 111 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (111, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false FROM course_legs WHERE course_id = 111 AND leg_seq = 4;

-- ── 3-10 (3곳 · 9경 1곳) : 양지암조각공원 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (112, '양지암조각공원 · 기성관 · 바람의언덕', '고현터미널 11:00 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-10', 3, 10,
        1, '11:00', '19:40', 520, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (112, 16, 1, true, '11:40', '12:40', 60);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (112, 19, 2, true, '13:50', '15:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (112, 1, 3, true, '16:10', '18:48', 158);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (112, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false FROM course_legs WHERE course_id = 112 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (112, 2, 16, 19, 'BUS', '12:40', '13:50', 70, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '12:40', false, '거제', '13:50', false FROM course_legs WHERE course_id = 112 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (112, 3, 19, 1, 'BUS', '15:25', '16:10', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55-1', '거제', '15:25', false, '도장포', '16:10', true FROM course_legs WHERE course_id = 112 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (112, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false FROM course_legs WHERE course_id = 112 AND leg_seq = 4;

-- ── 4-01 (4곳 · 9경 2곳) : 학동 · 씨월드 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (113, '학동 · 씨월드 · 기성관 · 바람의언덕', '고현터미널 11:05 출발 → 20:55 복귀 · 약 10시간', NULL, '4-01', 4, 1,
        2, '11:05', '20:55', 590, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (113, 4, 1, true, '11:45', '13:10', 85);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (113, 18, 2, true, '13:37', '15:00', 83);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (113, 19, 3, true, '15:50', '17:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (113, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (113, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false FROM course_legs WHERE course_id = 113 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (113, 2, 4, 18, 'BUS', '13:10', '13:37', 27, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '학동', '13:10', false, '지세포', '13:37', false FROM course_legs WHERE course_id = 113 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (113, 3, 18, 19, 'BUS', '15:00', '15:50', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '거제', '15:50', false FROM course_legs WHERE course_id = 113 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (113, 4, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '17:25', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 113 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (113, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 113 AND leg_seq = 5;

-- ── 4-02 (4곳 · 9경 2곳) : 씨월드 · 양지암조각공원 · 학동 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (114, '씨월드 · 양지암조각공원 · 학동 · 바람의언덕', '고현터미널 11:04 출발 → 20:55 복귀 · 약 10시간', NULL, '4-02', 4, 2,
        2, '11:04', '20:55', 591, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (114, 18, 1, true, '11:48', '12:57', 69);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (114, 16, 2, true, '13:18', '14:40', 82);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (114, 4, 3, true, '15:30', '17:45', 135);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (114, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (114, 1, NULL, 18, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 114 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (114, 2, 18, 16, 'BUS', '12:57', '13:18', 21, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '60', '지세포', '12:57', false, '능포', '13:18', false FROM course_legs WHERE course_id = 114 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (114, 3, 16, 4, 'BUS', '14:40', '15:30', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '14:40', false, '학동', '15:30', false FROM course_legs WHERE course_id = 114 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (114, 4, 4, 1, 'BUS', '17:45', '17:55', 10, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '17:45', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 114 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (114, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 114 AND leg_seq = 5;

-- ── 4-03 (4곳 · 9경 1곳) : 매미성 · 양지암조각공원 · 씨월드 · 학동
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (115, '매미성 · 양지암조각공원 · 씨월드 · 학동', '고현터미널 11:02 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '4-03', 4, 3,
        1, '11:02', '19:40', 518, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (115, 7, 1, true, '11:47', '13:37', 110);   -- 매미성
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (115, 16, 2, true, '14:30', '15:40', 70);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (115, 18, 3, true, '16:00', '17:00', 60);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (115, 4, 4, true, '17:25', '19:00', 95);   -- 학동
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (115, 1, NULL, 7, 'BUS', '11:02', '11:47', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '33', '고현', '11:02', false, '대금교차로', '11:47', true FROM course_legs WHERE course_id = 115 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (115, 2, 7, 16, 'BUS', '13:37', '14:30', 53, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '33', '대금교차로', '13:37', true, '능포', '14:30', false FROM course_legs WHERE course_id = 115 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (115, 3, 16, 18, 'BUS', '15:40', '16:00', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '61', '능포', '15:40', false, '지세포', '16:00', false FROM course_legs WHERE course_id = 115 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (115, 4, 18, 4, 'BUS', '17:00', '17:25', 25, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '17:00', false, '학동', '17:25', false FROM course_legs WHERE course_id = 115 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (115, 5, 4, NULL, 'BUS', '19:00', '19:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '19:00', false, '고현', '19:40', false FROM course_legs WHERE course_id = 115 AND leg_seq = 5;

-- ── 4-04 (4곳 · 9경 1곳) : 양지암조각공원 · 조선해양문화관 · 씨월드 · 학동
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (116, '양지암조각공원 · 조선해양문화관 · 씨월드 · 학동', '고현터미널 11:00 출발 → 17:30 복귀 · 약 6시간 30분', NULL, '4-04', 4, 4,
        1, '11:00', '17:30', 390, 390,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (116, 16, 1, true, '11:40', '12:40', 60);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (116, 20, 2, true, '13:00', '14:00', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (116, 18, 3, true, '14:00', '15:00', 60);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (116, 4, 4, true, '15:30', '16:50', 80);   -- 학동
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (116, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false FROM course_legs WHERE course_id = 116 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (116, 2, 16, 20, 'BUS', '12:40', '13:00', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '12:40', false, '지세포', '13:00', false FROM course_legs WHERE course_id = 116 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (116, 3, 20, 18, 'SAME_STOP', '14:00', '14:00', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (116, 4, 18, 4, 'BUS', '15:00', '15:30', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '학동', '15:30', false FROM course_legs WHERE course_id = 116 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (116, 5, 4, NULL, 'BUS', '16:50', '17:30', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '16:50', false, '고현', '17:30', false FROM course_legs WHERE course_id = 116 AND leg_seq = 5;

-- ── 4-05 (4곳 · 9경 1곳) : 씨월드 · 양지암조각공원 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (117, '씨월드 · 양지암조각공원 · 기성관 · 바람의언덕', '고현터미널 11:04 출발 → 20:55 복귀 · 약 10시간', NULL, '4-05', 4, 5,
        1, '11:04', '20:55', 591, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (117, 18, 1, true, '11:48', '12:57', 69);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (117, 16, 2, true, '13:18', '14:40', 82);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (117, 19, 3, true, '15:50', '17:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (117, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (117, 1, NULL, 18, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 117 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (117, 2, 18, 16, 'BUS', '12:57', '13:18', 21, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '60', '지세포', '12:57', false, '능포', '13:18', false FROM course_legs WHERE course_id = 117 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (117, 3, 16, 19, 'BUS', '14:40', '15:50', 70, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '14:40', false, '거제', '15:50', false FROM course_legs WHERE course_id = 117 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (117, 4, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '17:25', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 117 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (117, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 117 AND leg_seq = 5;

-- ── 4-06 (4곳 · 9경 1곳) : 조선해양문화관 · 씨월드 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (118, '조선해양문화관 · 씨월드 · 기성관 · 바람의언덕', '고현터미널 11:04 출발 → 20:55 복귀 · 약 10시간', NULL, '4-06', 4, 6,
        1, '11:04', '20:55', 591, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (118, 20, 1, true, '11:48', '12:48', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (118, 18, 2, true, '12:48', '15:00', 132);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (118, 19, 3, true, '15:50', '17:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (118, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (118, 1, NULL, 20, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 118 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (118, 2, 20, 18, 'SAME_STOP', '12:48', '12:48', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (118, 3, 18, 19, 'BUS', '15:00', '15:50', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '거제', '15:50', false FROM course_legs WHERE course_id = 118 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (118, 4, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '17:25', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 118 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (118, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 118 AND leg_seq = 5;

-- ── 4-07 (4곳 · 9경 0곳) : 매미성 · 양지암조각공원 · 조선해양문화관 · 씨월드
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (119, '매미성 · 양지암조각공원 · 조선해양문화관 · 씨월드', '고현터미널 11:02 출발 → 18:50 복귀 · 약 8시간', NULL, '4-07', 4, 7,
        0, '11:02', '18:50', 468, 480,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (119, 7, 1, true, '11:47', '13:37', 110);   -- 매미성
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (119, 16, 2, true, '14:30', '15:40', 70);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (119, 20, 3, true, '16:00', '17:00', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (119, 18, 4, true, '17:00', '18:05', 65);   -- 씨월드
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (119, 1, NULL, 7, 'BUS', '11:02', '11:47', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '33', '고현', '11:02', false, '대금교차로', '11:47', true FROM course_legs WHERE course_id = 119 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (119, 2, 7, 16, 'BUS', '13:37', '14:30', 53, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '33', '대금교차로', '13:37', true, '능포', '14:30', false FROM course_legs WHERE course_id = 119 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (119, 3, 16, 20, 'BUS', '15:40', '16:00', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '61', '능포', '15:40', false, '지세포', '16:00', false FROM course_legs WHERE course_id = 119 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (119, 4, 20, 18, 'SAME_STOP', '17:00', '17:00', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (119, 5, 18, NULL, 'BUS', '18:05', '18:50', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22', '지세포', '18:05', false, '고현', '18:50', false FROM course_legs WHERE course_id = 119 AND leg_seq = 5;

-- ── 4-08 (4곳 · 9경 0곳) : 씨월드 · 양지암조각공원 · 맹종죽테마파크 · 매미성
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (120, '씨월드 · 양지암조각공원 · 맹종죽테마파크 · 매미성', '고현터미널 11:04 출발 → 19:59 복귀 · 약 9시간', NULL, '4-08', 4, 8,
        0, '11:04', '19:59', 535, 540,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (120, 18, 1, true, '11:48', '12:57', 69);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (120, 16, 2, true, '13:18', '14:22', 64);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (120, 17, 3, true, '15:35', '17:22', 107);   -- 맹종죽테마파크
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (120, 7, 4, true, '17:47', '19:09', 82);   -- 매미성
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (120, 1, NULL, 18, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 120 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (120, 2, 18, 16, 'BUS', '12:57', '13:18', 21, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '60', '지세포', '12:57', false, '능포', '13:18', false FROM course_legs WHERE course_id = 120 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (120, 3, 16, 17, 'BUS', '14:22', '15:35', 73, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '32', '능포', '14:22', false, '맹종죽테마파크', '15:35', true FROM course_legs WHERE course_id = 120 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (120, 4, 17, 7, 'BUS', '17:22', '17:47', 25, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '33', '맹종죽테마파크', '17:22', true, '대금교차로', '17:47', true FROM course_legs WHERE course_id = 120 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (120, 5, 7, NULL, 'BUS', '19:09', '19:59', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '32-1', '대금교차로', '19:09', true, '고현', '19:59', false FROM course_legs WHERE course_id = 120 AND leg_seq = 5;

-- ── 4-09 (4곳 · 9경 2곳) : 조선해양문화관 · 씨월드 · 학동 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (121, '조선해양문화관 · 씨월드 · 학동 · 바람의언덕', '고현터미널 11:04 출발 → 20:55 복귀 · 약 10시간', NULL, '4-09', 4, 9,
        2, '11:04', '20:55', 591, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (121, 20, 1, true, '11:48', '12:48', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (121, 18, 2, true, '12:48', '15:00', 132);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (121, 4, 3, true, '15:30', '17:45', 135);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (121, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (121, 1, NULL, 20, 'BUS', '11:04', '11:48', 44, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '22-1', '고현', '11:04', false, '지세포', '11:48', false FROM course_legs WHERE course_id = 121 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (121, 2, 20, 18, 'SAME_STOP', '12:48', '12:48', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (121, 3, 18, 4, 'BUS', '15:00', '15:30', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '학동', '15:30', false FROM course_legs WHERE course_id = 121 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (121, 4, 4, 1, 'BUS', '17:45', '17:55', 10, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '17:45', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 121 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (121, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 121 AND leg_seq = 5;

-- ── 4-10 (4곳 · 9경 2곳) : 양지암조각공원 · 학동 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (122, '양지암조각공원 · 학동 · 기성관 · 바람의언덕', '고현터미널 11:00 출발 → 20:55 복귀 · 약 10시간', NULL, '4-10', 4, 10,
        2, '11:00', '20:55', 595, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (122, 16, 1, true, '11:40', '12:40', 60);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (122, 4, 2, true, '13:25', '15:00', 95);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (122, 19, 3, true, '15:20', '17:25', 125);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (122, 1, 4, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (122, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false FROM course_legs WHERE course_id = 122 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (122, 2, 16, 4, 'BUS', '12:40', '13:25', 45, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '12:40', false, '학동', '13:25', false FROM course_legs WHERE course_id = 122 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (122, 3, 4, 19, 'BUS', '15:00', '15:20', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '15:00', false, '거제', '15:20', false FROM course_legs WHERE course_id = 122 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (122, 4, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '17:25', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 122 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (122, 5, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 122 AND leg_seq = 5;

-- ── 5-01 (5곳 · 9경 2곳) : 양지암조각공원 · 조선해양문화관 · 씨월드 · 학동 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (102, '양지암조각공원 · 조선해양문화관 · 씨월드 · 학동 · 바람의언덕', '고현터미널 11:00 출발 → 20:55 복귀 · 약 10시간', NULL, '5-01', 5, 1,
        2, '11:00', '20:55', 595, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 16, 1, true, '11:40', '12:40', 60);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 20, 2, true, '13:00', '14:00', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 18, 3, true, '14:00', '15:00', 60);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 4, 4, true, '15:30', '17:45', 135);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 1, 5, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false FROM course_legs WHERE course_id = 102 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 2, 16, 20, 'BUS', '12:40', '13:00', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '12:40', false, '지세포', '13:00', false FROM course_legs WHERE course_id = 102 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 3, 20, 18, 'SAME_STOP', '14:00', '14:00', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 4, 18, 4, 'BUS', '15:00', '15:30', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '학동', '15:30', false FROM course_legs WHERE course_id = 102 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 5, 4, 1, 'BUS', '17:45', '17:55', 10, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '학동', '17:45', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 102 AND leg_seq = 5;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (102, 6, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 102 AND leg_seq = 6;

-- ── 5-02 (5곳 · 9경 1곳) : 기성관 · 학동 · 조선해양문화관 · 씨월드 · 양지암조각공원
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (123, '기성관 · 학동 · 조선해양문화관 · 씨월드 · 양지암조각공원', '고현터미널 11:05 출발 → 19:35 복귀 · 약 8시간 30분', NULL, '5-02', 5, 2,
        1, '11:05', '19:35', 510, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (123, 19, 1, true, '11:25', '12:48', 83);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (123, 4, 2, true, '13:10', '15:00', 110);   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (123, 20, 3, true, '15:27', '16:27', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (123, 18, 4, true, '16:27', '17:35', 68);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (123, 16, 5, true, '17:56', '18:56', 60);   -- 양지암조각공원
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 1, NULL, 19, 'BUS', '11:05', '11:25', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '고현', '11:05', false, '거제', '11:25', false FROM course_legs WHERE course_id = 123 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 2, 19, 4, 'BUS', '12:48', '13:10', 22, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '거제', '12:48', false, '학동', '13:10', false FROM course_legs WHERE course_id = 123 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 3, 4, 20, 'BUS', '15:00', '15:27', 27, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '학동', '15:00', false, '지세포', '15:27', false FROM course_legs WHERE course_id = 123 AND leg_seq = 3;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 4, 20, 18, 'SAME_STOP', '16:27', '16:27', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 5, 18, 16, 'BUS', '17:35', '17:56', 21, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '17:35', false, '능포', '17:56', false FROM course_legs WHERE course_id = 123 AND leg_seq = 5;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (123, 6, 16, NULL, 'BUS', '18:56', '19:35', 39, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '능포', '18:56', false, '고현', '19:35', false FROM course_legs WHERE course_id = 123 AND leg_seq = 6;

-- ── 5-03 (5곳 · 9경 1곳) : 양지암조각공원 · 조선해양문화관 · 씨월드 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file, enabled)
VALUES (103, '양지암조각공원 · 조선해양문화관 · 씨월드 · 기성관 · 바람의언덕', '고현터미널 11:00 출발 → 20:55 복귀 · 약 10시간', NULL, '5-03', 5, 3,
        1, '11:00', '20:55', 595, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt', true)
ON CONFLICT (course_id) DO UPDATE SET
  course_name = EXCLUDED.course_name, summary = EXCLUDED.summary, theme = EXCLUDED.theme,
  course_code = EXCLUDED.course_code, spot_count = EXCLUDED.spot_count, rank_no = EXCLUDED.rank_no,
  nine_scenic_count = EXCLUDED.nine_scenic_count, depart_time = EXCLUDED.depart_time,
  return_time = EXCLUDED.return_time, total_min = EXCLUDED.total_min,
  approx_total_min = EXCLUDED.approx_total_min, service = EXCLUDED.service,
  base_date = EXCLUDED.base_date, origin_stop = EXCLUDED.origin_stop,
  source_file = EXCLUDED.source_file, enabled = EXCLUDED.enabled;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 16, 1, true, '11:40', '12:40', 60);   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 20, 2, true, '13:00', '14:00', 60);   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 18, 3, true, '14:00', '15:00', 60);   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 19, 4, true, '15:50', '17:25', 95);   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 1, 5, true, '17:55', '20:05', 130);   -- 바람의언덕
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false FROM course_legs WHERE course_id = 103 AND leg_seq = 1;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 2, 16, 20, 'BUS', '12:40', '13:00', 20, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '능포', '12:40', false, '지세포', '13:00', false FROM course_legs WHERE course_id = 103 AND leg_seq = 2;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 3, 20, 18, 'SAME_STOP', '14:00', '14:00', 0, 0, 0);
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 4, 18, 19, 'BUS', '15:00', '15:50', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '거제', '15:50', false FROM course_legs WHERE course_id = 103 AND leg_seq = 4;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 5, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '거제', '17:25', false, '도장포', '17:55', true FROM course_legs WHERE course_id = 103 AND leg_seq = 5;
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min) VALUES (103, 6, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0);
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated) SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false FROM course_legs WHERE course_id = 103 AND leg_seq = 6;

SELECT setval('courses_course_id_seq', (SELECT MAX(course_id) FROM courses));
