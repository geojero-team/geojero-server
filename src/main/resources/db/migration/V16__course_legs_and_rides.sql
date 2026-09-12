-- V16 (2026-09-12): 추천 코스 — 구간별 노선·시각·추정 여부를 담는 구조
--
-- 왜 필요한가
--   판정 제거(2026-09-12) 후 제품이 제시하는 것은 「우리가 짠 코스 + 구간 이동 정보」다.
--   기존 courses/course_pois 는 "어떤 스팟을 어떤 순서로"만 담아, 구간의 노선 번호·시각·
--   추정 여부를 실을 곳이 없었다(course_pois 0행). 팀원이 BIS 원문에서 계산한
--   recommended_courses.json 의 모양을 그대로 담는다.
--
-- 설계 원칙 — 코스가 3개에서 30개로 늘어도 이 구조는 바뀌지 않아야 한다
--   * legs 1:N rides — 환승은 제품에서 쓰지 않기로 확정했으나(2026-09-12 팀 결정) 구조는
--     담을 수 있게 둔다. rides 가 둘 이상이면 환승이고 course_legs.transfers 가 그 수다.
--     지금 적재하는 코스는 전부 transfers = 0 이다.
--   * estimated 를 ride 의 승차·하차 각각에 둔다. 원문 시간표에 시각 칸이 없는 정류장
--     (도장포·식물원·대금교차로·포로수용소·옥포대첩기념공원·맹종죽테마파크)은 앞뒤 정류장
--     시각으로 감싼 값이다 — 하차는 뒤 정류장(상한), 승차는 앞 정류장(하한)이라
--     버스를 놓치지 않는 쪽으로만 틀린다. 이 플래그가 없으면 화면이 추정을 확정처럼
--     말하게 된다(절대규칙 1: 기준문서에 없는 수치를 만들지 않는다).
--   * mode = SAME_STOP — 거제조선해양문화관과 거제씨월드는 같은 '신촌' 정류장이라
--     버스 구간이 아예 없다. 구간을 만들지 않으면 화면이 '이유 없는 빈칸'을 그리게 되므로
--     (절대규칙 3) "같은 정류장"이라고 명시한 구간을 만든다. duration_min = 0.
--   * from_poi_id / to_poi_id 가 NULL 이면 출발·복귀 지점(고현터미널)이다.
--     courses.origin_stop 이 그 정류장 이름을 적는다.

CREATE TYPE course_service AS ENUM ('WEEKDAY', 'HOLIDAY');
CREATE TYPE leg_mode AS ENUM ('BUS', 'SAME_STOP');

-- 1) courses 확장 --------------------------------------------------------------
-- 기존 3행(§3 검증 코스 — CourseSeeder 가 넣는 상수)은 아래 컬럼이 전부 NULL 이다.
-- course_code IS NULL 이 곧 "검증 코스", NOT NULL 이 "계산된 추천 코스"다.
ALTER TABLE courses
  ADD COLUMN course_code       varchar(10),
  ADD COLUMN spot_count        int,
  ADD COLUMN rank_no           int,
  ADD COLUMN nine_scenic_count int,
  ADD COLUMN depart_time       time,
  ADD COLUMN return_time       time,
  ADD COLUMN total_min         int,
  ADD COLUMN approx_total_min  int,
  ADD COLUMN service           course_service,
  ADD COLUMN base_date         date,
  ADD COLUMN origin_stop       varchar(50),
  ADD COLUMN source_file       varchar(255);

-- 추천 코스에는 테마가 없다(스팟 조합으로 계산된 것이라 우리가 붙인 분류가 아니다).
ALTER TABLE courses ALTER COLUMN theme DROP NOT NULL;

CREATE UNIQUE INDEX idx_courses_code ON courses (course_code)
  WHERE course_code IS NOT NULL;
CREATE INDEX idx_courses_spotcount_rank ON courses (spot_count, rank_no)
  WHERE course_code IS NOT NULL;

COMMENT ON COLUMN courses.course_code IS
  '팀원 산출물의 코스 id("3-01" = 3곳-01번). NULL 이면 §3 검증 코스 3종이다.';
COMMENT ON COLUMN courses.total_min IS
  '출발부터 복귀까지 실제 분. 화면에는 approx_total_min 을 쓴다.';
COMMENT ON COLUMN courses.approx_total_min IS
  '화면 표시용 반올림 분("약 8시간 30분" = 510). 원문 계산값이라 우리가 다시 굴리지 않는다.';
COMMENT ON COLUMN courses.service IS
  '어느 요일 시간표로 계산했는가. 현재 WEEKDAY 뿐 — 주말·공휴일용은 따로 계산해야 한다.';
COMMENT ON COLUMN courses.base_date IS
  '근거 시간표의 시행일(2026-08-18). 개편되면 코스를 다시 계산해야 한다는 신호다.';

-- 2) course_pois 확장 — 스팟별 도착·출발·체류 ---------------------------------
ALTER TABLE course_pois
  ADD COLUMN arrive_time time,
  ADD COLUMN leave_time  time,
  ADD COLUMN stay_min    int;

COMMENT ON COLUMN course_pois.stay_min IS
  '이 코스에서 이 스팟에 머무는 분. 정류장↔스팟 도보 시간이 포함돼 있다. '
  'pois.stay_min(스팟 자체의 권장 체류)과 다른 값이다 — 버스 시각에 맞추느라 길어진다.';

-- 3) course_legs — 구간 ---------------------------------------------------------
CREATE TABLE course_legs (
  leg_id            bigserial PRIMARY KEY,
  course_id         bigint NOT NULL REFERENCES courses ON DELETE CASCADE,
  leg_seq           int NOT NULL,
  from_poi_id       bigint REFERENCES pois ON DELETE RESTRICT,
  to_poi_id         bigint REFERENCES pois ON DELETE RESTRICT,
  mode              leg_mode NOT NULL,
  depart_time       time,
  arrive_time       time,
  duration_min      int NOT NULL,
  transfers         int NOT NULL DEFAULT 0,
  transfer_wait_min int NOT NULL DEFAULT 0,
  UNIQUE (course_id, leg_seq),
  CONSTRAINT chk_leg_duration CHECK (duration_min >= 0),
  CONSTRAINT chk_leg_transfers CHECK (transfers >= 0 AND transfer_wait_min >= 0),
  -- 출발과 도착이 같은 스팟인 구간은 없다. 둘 다 NULL(고현→고현)도 없다.
  CONSTRAINT chk_leg_endpoints CHECK (
    from_poi_id IS DISTINCT FROM to_poi_id
    AND NOT (from_poi_id IS NULL AND to_poi_id IS NULL)),
  -- SAME_STOP 은 버스를 타지 않으므로 0분이고, 옮겨가는 시각 하나만 있다
  -- (원문도 depart == arrive 로 온다 — 예: 14:00~14:00).
  CONSTRAINT chk_leg_same_stop CHECK (
    mode <> 'SAME_STOP'
    OR (duration_min = 0 AND transfers = 0 AND transfer_wait_min = 0
        AND depart_time IS NOT NULL AND depart_time = arrive_time)),
  CONSTRAINT chk_leg_bus CHECK (
    mode <> 'BUS' OR (depart_time IS NOT NULL AND arrive_time IS NOT NULL))
);
CREATE INDEX idx_course_legs_course ON course_legs (course_id, leg_seq);

COMMENT ON TABLE course_legs IS
  '코스의 한 구간. from/to 가 NULL 이면 고현터미널(courses.origin_stop). '
  '자정을 넘는 구간은 현재 없다 — 생기면 날짜 오프셋 컬럼이 필요하다.';
COMMENT ON COLUMN course_legs.transfers IS
  '이 구간에서 갈아탄 횟수. 환승은 제품에서 쓰지 않기로 확정했으므로 적재분은 전부 0이다. '
  '0 이 아닌 행이 생기면 화면이 구간 하나에 버스 여러 대를 그려야 한다.';
COMMENT ON COLUMN course_legs.duration_min IS
  '승차부터 하차까지 분(환승 대기 포함). SAME_STOP 은 0.';

-- 4) course_rides — 구간 안에서 실제로 탄 버스 -----------------------------------
CREATE TABLE course_rides (
  ride_id          bigserial PRIMARY KEY,
  leg_id           bigint NOT NULL REFERENCES course_legs ON DELETE CASCADE,
  ride_seq         int NOT NULL,
  route_no         varchar(20) NOT NULL,
  board_stop       varchar(50) NOT NULL,
  board_time       time NOT NULL,
  board_estimated  boolean NOT NULL,
  alight_stop      varchar(50) NOT NULL,
  alight_time      time NOT NULL,
  alight_estimated boolean NOT NULL,
  UNIQUE (leg_id, ride_seq)
);
CREATE INDEX idx_course_rides_leg ON course_rides (leg_id, ride_seq);

COMMENT ON TABLE course_rides IS
  '구간 안에서 실제로 탄 버스. 구간당 1행이 원칙(환승 없음). '
  'board_stop/alight_stop 은 시간표 원문의 정류장 이름이라 stops.stop_name 과 맞춰 읽을 수 있다. '
  '단 FK 는 걸지 않는다 — 시간표 재조사로 stops 가 전량 교체될 예정이고(기준문서 §9), '
  'stop_id 가 바뀌어도 코스가 깨지지 않아야 한다.';
COMMENT ON COLUMN course_rides.board_estimated IS
  'true 면 원문 시간표에 이 정류장의 시각 칸이 없어 앞 정류장 시각(하한)으로 잡은 값이다. '
  '실제 버스는 이 시각보다 늦게 온다. 화면은 확정 시각과 갈라 말해야 한다.';
COMMENT ON COLUMN course_rides.alight_estimated IS
  'true 면 뒤 정류장 시각(상한)으로 잡은 값이다. 실제 버스는 이 시각보다 일찍 닿는다.';
