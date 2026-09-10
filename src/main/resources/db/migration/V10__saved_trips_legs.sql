-- 저장 일정이 '검증 코스 3종'에만 묶여 있던 것을 푼다.
--
-- 왜: 화면은 사용자가 고른 스팟으로 코스를 조립하는데, saved_trips는 course_id NOT NULL이라
-- courses 테이블의 3행 중 하나여야만 저장됐다. 그래서 학동을 골라 저장을 누르면 붙일 이름표가
-- 없었다. 억지로 아무 course_id나 붙이면 '바람의언덕을 저장했는데 서울발 무박 일출이 저장'된다.
--
-- 판정 엔진은 이미 임의 구간을 판정할 수 있다(POST /api/judge가 구간 목록을 그대로 받는다).
-- 막혀 있던 건 저장 테이블뿐이었다.
--
-- legs: 판정에 넣을 구간 목록 원본. 이게 있어야 '오늘 기준 재판정'이 성립한다 —
--       무엇을 다시 계산해야 하는지 모르면 재판정이 불가능하다.
-- title·chain: 저장 시점에 화면이 보여주던 이름과 경로. 저장 목록 카드가 쓴다.
--       코스 이름을 나중에 바꿔도 "내가 저장한 그것"이 그대로 남아야 하므로 스냅샷으로 둔다.

ALTER TABLE saved_trips
  ALTER COLUMN course_id DROP NOT NULL,
  ADD COLUMN legs  jsonb,
  ADD COLUMN title varchar(120),
  ADD COLUMN chain varchar(300);

-- 판정할 근거가 없는 행은 만들지 않는다. 둘 중 하나는 반드시 있어야 한다.
ALTER TABLE saved_trips
  ADD CONSTRAINT saved_trips_source_ck
  CHECK (course_id IS NOT NULL OR legs IS NOT NULL);
