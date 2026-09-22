-- V53 (2026-09-22): 도선 구간이 **선착장을 가리킨다** — course_legs.shuttle_id 와 그 제약
--
-- V52 가 더한 leg_mode 값 'SHUTTLE' 을 여기서 처음 쓴다. 한 파일에 몰지 못하는 이유는 V52 주석에 있다
-- (enum 값을 더한 트랜잭션 안에서는 그 값을 쓸 수 없다 — 55P04).
--
-- 시각은 넣는다 — 도선은 유람선과 달리 **출발 시각이 날짜와 무관하게 고정**이라(shuttle_departures)
-- 구간에 시각을 적을 수 있다. 유람선 구간이 시각을 비우는 이유(날짜마다 편이 다름)가 여기엔 없다.

ALTER TABLE course_legs
  ADD COLUMN shuttle_id smallint REFERENCES shuttle_docks (shuttle_id);

COMMENT ON COLUMN course_legs.shuttle_id IS
  '도선 선착장(shuttle_docks). SHUTTLE 구간에만 있다 — 화면이 운항사 · 요금 · 예약처를 여기서 읽는다.';

-- SHUTTLE 이면 선착장을 가리키고, 가리키면 SHUTTLE 이다(FERRY 쪽 규칙과 같은 모양).
ALTER TABLE course_legs ADD CONSTRAINT chk_leg_shuttle_dock
  CHECK ((mode = 'SHUTTLE') = (shuttle_id IS NOT NULL));

-- 도선 구간은 시각을 적는다 — 적지 않으면 「몇 시 배인지」를 화면이 말할 수 없다.
ALTER TABLE course_legs ADD CONSTRAINT chk_leg_shuttle_time
  CHECK (mode <> 'SHUTTLE' OR (depart_time IS NOT NULL AND arrive_time IS NOT NULL));
