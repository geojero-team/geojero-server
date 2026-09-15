-- V31 (2026-09-15): 도선 시간표 — 내도(구조라 ↔ 내도) · 지심도(장승포 지심도 터미널 ↔ 지심도) (사용자 결정 · 사용자 입력)
--
-- V30 에서 9경 7경 · 8경을 스팟으로 넣으며 시간표를 비워 두었다(TIMETABLE_PENDING). 팀원이 바빠 사용자가 운항사 안내를
-- 직접 옮겨 주었다(2026-09-15 대화). 값은 그 원문 그대로다 — 시각 · 요일 구분 · 막배 · 요금 · 전화 · 예약 · 안내 문구.
--
-- 외도 유람선 표(V23 ferry_*)에 넣지 않는 이유
--   · 외도 배는 **날짜마다 원문**이 있고(운항사 월간 공개) 왕복이라 복귀 시각을 출항 + 총 소요시간으로 계산한다.
--   · 도선은 **요일별 고정 시각**이고 들어가는 배 · 나오는 배가 따로 정해져 있다. 내도 막배(17:00 → 17:10 「막배만 20분 빠름」)처럼
--     출항 + 상수로 계산되지 않는 편이 있어 외도 방식으로는 못 담는다. 공개 범위(ferry_coverage)도 없다.
--   그래서 표를 따로 두고, 화면(칩 · 선착장 줄 · 시간표 모양)만 외도와 맞춘다.
--
-- 요일: 요청 날짜의 평일/휴일은 버스와 같은 판정(SnapshotService dayClass — 주말 · 공휴일이 HOLIDAY)을 쓴다.
--   · 지심도 — 「평일, 주말 동일」 → day_type ALL
--   · 내도 — 「주중」에만 시각이 있다. 「주말, 공휴일」 칸은 시각이 아니라 「5번~8번 (주말 수시운행)」이다 →
--     HOLIDAY 행을 만들지 않고 holiday_note 에 원문을 둔다. 평일 시각을 주말에 쓰지 않는다(없는 값을 만들지 않는다).
-- 선착장 짧은 이름(dock_name)은 칩 · 목록 둘째 줄(「구조라 선착장」)에 쓴다. 지심도 터미널은 장승포동이라 「장승포」다 —
--   외도 유람선의 장승포 선착장과 운항사가 다르므로 주소 줄에 「지심도 터미널」 원문을 그대로 둔다.

CREATE TABLE shuttle_docks (
  shuttle_id    smallserial  PRIMARY KEY,
  poi_id        bigint       NOT NULL REFERENCES pois,
  dock_name     varchar(20)  NOT NULL UNIQUE,
  island_name   varchar(20)  NOT NULL,
  operator_name varchar(50)  NOT NULL,
  address       varchar(200) NOT NULL,
  phone         varchar(20)  NOT NULL,
  trip_note     varchar(50),
  fare_text     varchar(200),
  booking_url   varchar(500),
  notice        varchar(300),
  holiday_note  varchar(100),
  source        varchar(100) NOT NULL,
  entered_on    date         NOT NULL
);
COMMENT ON TABLE shuttle_docks IS '도선 = 선착장 ↔ 섬 하나. 요일별 고정 시각(외도 유람선 ferry_* 와 다르다). poi_id 는 그 배로 가는 화면 스팟';
COMMENT ON COLUMN shuttle_docks.dock_name IS '칩 · 목록에 쓰는 선착장 짧은 이름(구조라 · 장승포). 화면은 「{dock} 선착장」';
COMMENT ON COLUMN shuttle_docks.island_name IS '배가 닿는 섬 이름. 스팟 이름과 다를 수 있다 — 스팟 「공곶이·내도」의 배는 내도로 간다';
COMMENT ON COLUMN shuttle_docks.trip_note IS '운항사 안내의 소요 · 관광 시간 문구 원문(「관광시간 10분」). 해석하지 않고 그대로 보인다';
COMMENT ON COLUMN shuttle_docks.holiday_note IS '주말 · 공휴일에 정해진 시각이 없을 때의 원문(「5번~8번 (주말 수시운행)」). 시각 행이 있으면 NULL';
COMMENT ON COLUMN shuttle_docks.source IS '화면 출처 줄. 값은 사용자가 운항사 안내를 옮겨 준 것(2026-09-15)';

CREATE TABLE shuttle_departures (
  shuttle_id  smallint    NOT NULL REFERENCES shuttle_docks,
  direction   varchar(3)  NOT NULL CHECK (direction IN ('IN', 'OUT')),
  day_type    varchar(7)  NOT NULL CHECK (day_type IN ('ALL', 'WEEKDAY', 'HOLIDAY')),
  depart_time time        NOT NULL,
  PRIMARY KEY (shuttle_id, direction, day_type, depart_time)
);
COMMENT ON COLUMN shuttle_departures.direction IS 'IN 선착장 → 섬(들어가는 배) · OUT 섬 → 선착장(나오는 배)';
COMMENT ON COLUMN shuttle_departures.day_type IS 'ALL 매일 · WEEKDAY 평일 · HOLIDAY 주말 · 공휴일(버스와 같은 판정)';

INSERT INTO shuttle_docks (poi_id, dock_name, island_name, operator_name, address, phone,
                           trip_note, fare_text, booking_url, notice, holiday_note, source, entered_on)
SELECT p.poi_id, v.dock_name, v.island_name, v.operator_name, v.address, v.phone,
       v.trip_note, v.fare_text, v.booking_url, v.notice, v.holiday_note, '운항사 안내', DATE '2026-09-15'
FROM (VALUES
  -- 7경 공곶이·내도(대표 공곶이 2536196) — 도선(구조라), 구조라 ↔ 내도 코스
  ('2536196', '구조라', '내도', '도선(구조라)', '경상남도 거제시 일운면 구조라로 21', '055-681-1624',
   '관광시간 10분', '대인 왕복 12,000원 · 소인 왕복 6,000원 (단체 40명 이상 10% 할인)', NULL, NULL,
   '5번~8번 (주말 수시운행)'),
  -- 8경 지심도(128035) — 장승포 지심도 터미널
  ('128035', '장승포', '지심도', '지심도 터미널', '경상남도 거제시 장승포로 56-22, 지심도 터미널', '055-681-6007',
   NULL, NULL, 'https://booking.naver.com/booking/12/bizes/56835?area=bns', '성수기 미예약 시, 탑승이 어려울 수 있습니다.',
   NULL)
) AS v(cid, dock_name, island_name, operator_name, address, phone, trip_note, fare_text, booking_url, notice, holiday_note)
JOIN pois p ON p.tour_content_id = v.cid;

INSERT INTO shuttle_departures (shuttle_id, direction, day_type, depart_time)
SELECT s.shuttle_id, v.direction, v.day_type, v.t::time
FROM (VALUES
  -- 내도 — 주중 들어가는 시간 · 나오는 시간(막배만 20분 빠름 → 17:10). 주말 · 공휴일은 holiday_note
  ('구조라', 'IN',  'WEEKDAY', '09:00'), ('구조라', 'IN',  'WEEKDAY', '11:00'), ('구조라', 'IN',  'WEEKDAY', '13:00'),
  ('구조라', 'IN',  'WEEKDAY', '15:00'), ('구조라', 'IN',  'WEEKDAY', '17:00'),
  ('구조라', 'OUT', 'WEEKDAY', '09:30'), ('구조라', 'OUT', 'WEEKDAY', '11:30'), ('구조라', 'OUT', 'WEEKDAY', '13:30'),
  ('구조라', 'OUT', 'WEEKDAY', '15:30'), ('구조라', 'OUT', 'WEEKDAY', '17:10'),
  -- 지심도 — 평일 · 주말 동일
  ('장승포', 'IN',  'ALL', '08:30'), ('장승포', 'IN',  'ALL', '10:30'), ('장승포', 'IN',  'ALL', '12:30'),
  ('장승포', 'IN',  'ALL', '14:30'), ('장승포', 'IN',  'ALL', '16:30'),
  ('장승포', 'OUT', 'ALL', '08:50'), ('장승포', 'OUT', 'ALL', '10:50'), ('장승포', 'OUT', 'ALL', '12:50'),
  ('장승포', 'OUT', 'ALL', '14:50'), ('장승포', 'OUT', 'ALL', '16:50')
) AS v(dock, direction, day_type, t)
JOIN shuttle_docks s ON s.dock_name = v.dock;
