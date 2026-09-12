-- V17 (2026-09-12): 추천 코스 3개 적재 — 환승 없고 배편이 필요 없는 것만
--
-- 출처: 팀원(김도현) 산출물 recommended_courses.json (recommended_courses.txt 확정본)
--       근거 시간표는 거제시 BIS 원문 2026-08-18, 평일 기준.
--
-- 왜 3개인가
--   팀원이 계산한 추천 코스는 30개다. 그중
--     * 환승이 있는 24개를 뺐다 — 환승은 제품에서 쓰지 않기로 확정했다(2026-09-12 팀 결정).
--       spot_times.json 은 직행만 남겼는데(58쌍, 나머지 214쌍은 NO_ROUTE) 코스 생성기가
--       그 제외 목록을 보지 않고 환승으로 이어붙인 것이다. 팀원이 다시 계산하기로 했다.
--     * 남은 6개 중 내도가 들어간 3개를 더 뺐다 — 내도는 구조라항에서 배를 타야 하고
--       배 시각이 어느 코스에도 반영돼 있지 않다(기준문서 §6: 배편이 없으면 가는 방법을
--       말할 수 없다). 배 시간표를 받으면 그 3개를 넣는다.
--   그래서 3곳 1개(3-01) · 5곳 2개(5-01·5-07)다. 4곳은 0개다 — 화면에 빈 상태가 필요하다.
--
-- course_id 101~ 을 명시로 쓴다. 1~3은 §3 검증 코스(CourseSeeder 가 넣는 상수)가 쓰고 있고
-- saved_trips.course_id 가 두 종류를 함께 가리켜야 한다.

-- ── 3-01 (3곳 · 9경 3곳) : 학동 · 해금강 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file)
VALUES (101, '학동 · 해금강 · 바람의언덕', '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분', NULL, '3-01', 3, 1,
        3, '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt')
ON CONFLICT (course_id) DO NOTHING;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 4, 1, true, '11:45', '13:45', 120) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 3, 2, true, '13:55', '16:38', 163) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 해금강
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (101, 1, 3, true, '16:50', '18:48', 118) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 바람의언덕

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (101, 1, NULL, 4, 'BUS', '11:05', '11:45', 40, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 고현터미널 → 학동
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '고현', '11:05', false, '학동', '11:45', false
  FROM course_legs WHERE course_id = 101 AND leg_seq = 1
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 101 AND cl.leg_seq = 1 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (101, 2, 4, 3, 'BUS', '13:45', '13:55', 10, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 학동 → 해금강
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '학동', '13:45', false, '해금강', '13:55', false
  FROM course_legs WHERE course_id = 101 AND leg_seq = 2
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 101 AND cl.leg_seq = 2 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (101, 3, 3, 1, 'BUS', '16:38', '16:50', 12, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 해금강 → 바람의언덕
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '해금강', '16:38', false, '도장포', '16:50', true
  FROM course_legs WHERE course_id = 101 AND leg_seq = 3
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 101 AND cl.leg_seq = 3 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (101, 4, 1, NULL, 'BUS', '18:48', '19:40', 52, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 바람의언덕 → 고현터미널
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false
  FROM course_legs WHERE course_id = 101 AND leg_seq = 4
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 101 AND cl.leg_seq = 4 AND cr.ride_seq = 1);

-- ── 5-01 (5곳 · 9경 2곳) : 양지암조각공원 · 조선해양문화관 · 씨월드 · 학동 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file)
VALUES (102, '양지암조각공원 · 조선해양문화관 · 씨월드 · 학동 · 바람의언덕', '고현터미널 11:00 출발 → 20:55 복귀 · 약 10시간', NULL, '5-01', 5, 1,
        2, '11:00', '20:55', 595, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt')
ON CONFLICT (course_id) DO NOTHING;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 16, 1, true, '11:40', '12:40', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 20, 2, true, '13:00', '14:00', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 18, 3, true, '14:00', '15:00', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 4, 4, true, '15:30', '17:45', 135) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 학동
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (102, 1, 5, true, '17:55', '20:05', 130) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 바람의언덕

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 고현터미널 → 양지암조각공원
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false
  FROM course_legs WHERE course_id = 102 AND leg_seq = 1
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 102 AND cl.leg_seq = 1 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 2, 16, 20, 'BUS', '12:40', '13:00', 20, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 양지암조각공원 → 조선해양문화관
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '67-1', '능포', '12:40', false, '지세포', '13:00', false
  FROM course_legs WHERE course_id = 102 AND leg_seq = 2
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 102 AND cl.leg_seq = 2 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 3, 20, 18, 'SAME_STOP', '14:00', '14:00', 0, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 조선해양문화관 → 씨월드

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 4, 18, 4, 'BUS', '15:00', '15:30', 30, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 씨월드 → 학동
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '학동', '15:30', false
  FROM course_legs WHERE course_id = 102 AND leg_seq = 4
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 102 AND cl.leg_seq = 4 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 5, 4, 1, 'BUS', '17:45', '17:55', 10, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 학동 → 바람의언덕
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '학동', '17:45', false, '도장포', '17:55', true
  FROM course_legs WHERE course_id = 102 AND leg_seq = 5
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 102 AND cl.leg_seq = 5 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (102, 6, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 바람의언덕 → 고현터미널
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false
  FROM course_legs WHERE course_id = 102 AND leg_seq = 6
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 102 AND cl.leg_seq = 6 AND cr.ride_seq = 1);

-- ── 5-07 (5곳 · 9경 1곳) : 양지암조각공원 · 조선해양문화관 · 씨월드 · 기성관 · 바람의언덕
INSERT INTO courses
  (course_id, course_name, summary, theme, course_code, spot_count, rank_no,
   nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
   service, base_date, origin_stop, source_file)
VALUES (103, '양지암조각공원 · 조선해양문화관 · 씨월드 · 기성관 · 바람의언덕', '고현터미널 11:00 출발 → 20:55 복귀 · 약 10시간', NULL, '5-07', 5, 7,
        1, '11:00', '20:55', 595, 600,
        'WEEKDAY', '2026-08-18', '고현', 'recommended_courses.txt')
ON CONFLICT (course_id) DO NOTHING;
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 16, 1, true, '11:40', '12:40', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 양지암조각공원
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 20, 2, true, '13:00', '14:00', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 조선해양문화관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 18, 3, true, '14:00', '15:00', 60) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 씨월드
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 19, 4, true, '15:50', '17:25', 95) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 기성관
INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES (103, 1, 5, true, '17:55', '20:05', 130) ON CONFLICT (course_id, poi_seq) DO NOTHING;   -- 바람의언덕

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 1, NULL, 16, 'BUS', '11:00', '11:40', 40, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 고현터미널 → 양지암조각공원
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '10', '고현', '11:00', false, '능포', '11:40', false
  FROM course_legs WHERE course_id = 103 AND leg_seq = 1
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 103 AND cl.leg_seq = 1 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 2, 16, 20, 'BUS', '12:40', '13:00', 20, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 양지암조각공원 → 조선해양문화관
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '67-1', '능포', '12:40', false, '지세포', '13:00', false
  FROM course_legs WHERE course_id = 103 AND leg_seq = 2
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 103 AND cl.leg_seq = 2 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 3, 20, 18, 'SAME_STOP', '14:00', '14:00', 0, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 조선해양문화관 → 씨월드

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 4, 18, 19, 'BUS', '15:00', '15:50', 50, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 씨월드 → 기성관
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '67-1', '지세포', '15:00', false, '거제면', '15:50', false
  FROM course_legs WHERE course_id = 103 AND leg_seq = 4
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 103 AND cl.leg_seq = 4 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 5, 19, 1, 'BUS', '17:25', '17:55', 30, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 기성관 → 바람의언덕
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '거제면', '17:25', false, '도장포', '17:55', true
  FROM course_legs WHERE course_id = 103 AND leg_seq = 5
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 103 AND cl.leg_seq = 5 AND cr.ride_seq = 1);

INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode, depart_time, arrive_time, duration_min, transfers, transfer_wait_min)
VALUES (103, 6, 1, NULL, 'BUS', '20:05', '20:55', 50, 0, 0)
ON CONFLICT (course_id, leg_seq) DO NOTHING;   -- 바람의언덕 → 고현터미널
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated, alight_stop, alight_time, alight_estimated)
SELECT leg_id, 1, '55', '도장포', '20:05', true, '고현', '20:55', false
  FROM course_legs WHERE course_id = 103 AND leg_seq = 6
  AND NOT EXISTS (SELECT 1 FROM course_rides cr JOIN course_legs cl ON cl.leg_id = cr.leg_id WHERE cl.course_id = 103 AND cl.leg_seq = 6 AND cr.ride_seq = 1);

-- 명시 id 로 넣었으니 시퀀스를 최댓값으로 맞춘다 (이후 자동 생성이 충돌하지 않게).
SELECT setval('courses_course_id_seq', (SELECT MAX(course_id) FROM courses));
