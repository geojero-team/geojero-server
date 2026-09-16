-- V36 (2026-09-16): 배 구간을 쓰는 첫 코스 — 「도장포에서 배로 외도보타니아」(3-11).
--
-- V35 가 leg_mode 에 FERRY 를 더했다. 여기서 ① 구간이 어느 배편인지 가리킬 칸을 만들고
-- ② 배가 낀 코스에서 같은 정류장 구간의 시각 제약을 풀고 ③ 코스를 하나 적재한다.
--
-- ── 배가 버스와 다른 점 둘 ────────────────────────────────────────────────────
--
-- 1) **떠난 선착장으로 돌아온다.** 외도 배는 전부 왕복이다 — 선착장 출항 → 외도 2시간 → 같은 선착장 복귀.
--    그래서 구간이 둘이다: 가는 구간(도장포 → 외도)과 돌아오는 구간(외도 → 도장포).
--    ⚠️ 원문이 주는 시간은 **왕복 + 외도 체류를 합친 총 소요시간 하나뿐**이다(도장포 외도상륙 = 160분,
--    그중 외도 체류 120분). 한 방향이 몇 분인지는 어디에도 없다. 그래서 **총 시간을 가는 구간에 싣고
--    돌아오는 구간은 0분**으로 둔다. 쪼개 만들지 않는다(절대규칙 1).
--
-- 2) **시각이 날짜마다 다르다.** 도장포 외도상륙 편은 공개된 48일 중 10:30 이 38일 · 14:00 이 36일이고
--    나머지는 09:00 · 11:20 · 12:40 · 15:10 … 제각각이다. 버스처럼 회차를 골라 박으면 그 날짜에만
--    맞는 말이 된다. 그래서 **FERRY 구간에는 시각을 넣지 않는다**(depart_time · arrive_time NULL).
--    화면은 그 자리에서 「시간표 ›」로 그날 배편을 보여준다(부록 G).
--
-- ── 이 코스가 성립하는 근거 ───────────────────────────────────────────────────
--
-- 고현터미널 11:05(55번) → 도장포 11:55 → 그날 오후 배 → 도장포 복귀 → 18:48(55번) → 고현터미널 19:40.
--   · 공개된 48일 **전부** 12시 이후 출항 편이 있다(실측). 11:55 에 닿아도 늘 오후 배를 탈 수 있다.
--   · 가장 늦은 출항은 15:10 이고 15:10 + 160분 = 17:50 이라 18:48 버스를 놓치지 않는다.
--   · 버스 시각은 다른 코스와 같이 회차를 골라 박는다(55번 평일 6회 중 11:05 · 18:48).
--
-- ── 코스 구성 ────────────────────────────────────────────────────────────────
--
-- 스팟 셋: 도장포유람선(선착장 · 9경 아님) → 외도보타니아(9경 3경) → 바람의언덕(9경 2경).
--   · **배가 스팟 순서의 가운데에 온다.** 배 다음에 버스를 타면 그 버스 시각을 정할 수 없다
--     (배가 몇 시에 돌아오는지 날짜마다 다르다). 그래서 배 뒤에는 **같은 정류장 구간과 복귀 버스**만 둔다 —
--     복귀는 막배 기준으로도 여유가 있는 18:48 하나로 고정한다.
--   · 바람의언덕과 도장포유람선은 **같은 도장포 정류장**이다(원문 「도보 1분거리에 바람의 언덕이 있습니다」).
--     배에서 돌아와 걸어서 옮긴다 — SAME_STOP.

-- 1) 구간이 어느 배편인지 ------------------------------------------------------
ALTER TABLE course_legs
  ADD COLUMN ferry_course_id int REFERENCES ferry_courses;

COMMENT ON COLUMN course_legs.ferry_course_id IS
  '이 구간이 타는 유람선 편(ferry_courses). 코스명 · 총 소요시간 · 외도 체류 · 예약 링크가 그 표에 있어 '
  '여기에 베껴 두지 않는다. BUS · SAME_STOP 구간은 NULL 이다.';

-- FERRY 면 배편이 있어야 하고, 아니면 없어야 한다.
ALTER TABLE course_legs ADD CONSTRAINT chk_leg_ferry_course
  CHECK ((mode = 'FERRY') = (ferry_course_id IS NOT NULL));

-- 배는 날짜마다 시각이 달라 구간에 박지 않는다.
ALTER TABLE course_legs ADD CONSTRAINT chk_leg_ferry_no_time
  CHECK (mode <> 'FERRY' OR (depart_time IS NULL AND arrive_time IS NULL));

-- 2) 같은 정류장 구간의 시각을 선택으로 ----------------------------------------
-- 전에는 SAME_STOP 에 시각이 반드시 있어야 했다. 앞뒤가 버스라 시각이 역산됐기 때문이다.
-- 배 뒤에 오는 SAME_STOP 은 **배가 몇 시에 돌아오는지 모르므로** 시각을 역산할 수 없다.
-- 자리값을 지어 넣지 않고 NULL 을 허용한다 — 있으면 depart = arrive 라는 규칙은 그대로다.
ALTER TABLE course_legs DROP CONSTRAINT chk_leg_same_stop;
ALTER TABLE course_legs ADD CONSTRAINT chk_leg_same_stop CHECK (
  mode <> 'SAME_STOP'
  OR (duration_min = 0 AND transfers = 0 AND transfer_wait_min = 0
      AND (depart_time IS NULL OR depart_time = arrive_time)));

-- 3) 코스 3-11 ----------------------------------------------------------------
INSERT INTO courses (course_id, course_name, summary, course_code, spot_count, rank_no,
                     nine_scenic_count, depart_time, return_time, total_min, approx_total_min,
                     service, base_date, origin_stop, source_file, title, intro)
VALUES (124,
        '도장포유람선 · 외도보타니아 · 바람의언덕',
        '고현터미널 11:05 출발 → 19:40 복귀 · 약 8시간 30분',
        '3-11', 3, 11, 2,
        '11:05', '19:40', 515, 510,
        'WEEKDAY', '2026-08-18', '고현',
        'BIS 55번 원문 + 외도유람선 예약센터(V24)',
        '배로 건너가는 거제 9경, 외도보타니아',
        '거제 9경 세 곳 중 외도보타니아는 배로만 갑니다. 고현터미널에서 55번으로 도장포 선착장까지 간 뒤 '
        '외도상륙 유람선을 타고 외도에서 두 시간을 보내고 같은 선착장으로 돌아옵니다. '
        '배에서 내리면 도보 1분 거리에 바람의언덕이 있어 돌아가는 버스를 기다리며 함께 봅니다.');

INSERT INTO course_pois (course_id, poi_id, poi_seq, is_fixed, arrive_time, leave_time, stay_min) VALUES
  (124, 2, 1, true, '11:55', NULL, NULL),   -- 도장포유람선 — 55번으로 도착
  (124, 5, 2, true, NULL,    NULL, NULL),   -- 외도보타니아 — 배 시각이 날짜마다 달라 시각 없음(체류 120분은 ferry_courses)
  (124, 1, 3, true, NULL,    '18:48', NULL); -- 바람의언덕 — 복귀 버스 시각만 확정

-- 구간 다섯. 3·4 가 배(왕복 한 덩어리)라 시각이 없다.
INSERT INTO course_legs (course_id, leg_seq, from_poi_id, to_poi_id, mode,
                         depart_time, arrive_time, duration_min, transfers, transfer_wait_min,
                         ferry_course_id) VALUES
  (124, 1, NULL, 2, 'BUS',       '11:05', '11:55',  50, 0, 0, NULL),
  (124, 2, 2,    5, 'FERRY',      NULL,    NULL,   160, 0, 0, 9),
  (124, 3, 5,    2, 'FERRY',      NULL,    NULL,     0, 0, 0, 9),
  (124, 4, 2,    1, 'SAME_STOP',  NULL,    NULL,     0, 0, 0, NULL),
  (124, 5, 1, NULL, 'BUS',       '18:48', '19:40',  52, 0, 0, NULL);

-- 탄 버스. 도장포는 원문 격자에 칸이 없어 앞뒤 정류장으로 감싼 값이다(3-01 과 같은 규칙).
INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated,
                          alight_stop, alight_time, alight_estimated)
SELECT l.leg_id, 1, '55', '고현', '11:05', false, '도장포', '11:55', true
FROM course_legs l WHERE l.course_id = 124 AND l.leg_seq = 1;

INSERT INTO course_rides (leg_id, ride_seq, route_no, board_stop, board_time, board_estimated,
                          alight_stop, alight_time, alight_estimated)
SELECT l.leg_id, 1, '55', '도장포', '18:48', true, '고현', '19:40', false
FROM course_legs l WHERE l.course_id = 124 AND l.leg_seq = 5;

-- 적재 확인 — 조용히 빠진 줄이 없는지 여기서 멈춰 잡는다.
DO $$ BEGIN
  IF (SELECT count(*) FROM course_legs WHERE course_id = 124) <> 5 THEN
    RAISE EXCEPTION 'V36: 3-11 구간이 5개가 아니다'; END IF;
  IF (SELECT count(*) FROM course_legs WHERE course_id = 124 AND mode = 'FERRY') <> 2 THEN
    RAISE EXCEPTION 'V36: 3-11 배 구간이 2개가 아니다'; END IF;
  IF (SELECT count(*) FROM course_rides r JOIN course_legs l ON l.leg_id = r.leg_id
      WHERE l.course_id = 124) <> 2 THEN
    RAISE EXCEPTION 'V36: 3-11 승차가 2개가 아니다'; END IF;
  IF (SELECT sum(duration_min) FROM course_legs WHERE course_id = 124 AND mode = 'BUS') <> 102 THEN
    RAISE EXCEPTION 'V36: 3-11 버스 합이 102분이 아니다'; END IF;
END $$;
