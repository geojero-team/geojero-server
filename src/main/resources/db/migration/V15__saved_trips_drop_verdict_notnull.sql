-- 판정 제거 (2026-09-12 팀 결정) — 저장이 판정 없이도 성립하게 만든다.
--
-- verdict_at_save 는 V1에서 jsonb NOT NULL 로 만들었다. "저장 시점 판정을 불변으로 남긴다"는
-- 의도였고, 시간표가 개편돼도 저장 당시의 답이 보존되게 하려던 것이다.
-- 판정을 내보내지 않기로 했으므로 더 이상 채울 값이 없는데, NOT NULL 이 남아 있으면
-- INSERT 자체가 실패한다. 그래서 제약만 푼다.
--
-- **컬럼은 지우지 않는다.** 기준문서 §6 컷 순서 1번이 이 항목을
--   "저장 일정 '저장 땐 성립 / 지금은 불성립' 비교 표시 → 컬럼(verdict_at_save)은 남기고 UI만 컷"
-- 으로 정해뒀다. 같은 방향이다 — 되살릴 때 스키마를 다시 만들 필요가 없다.
-- legs(V10)도 같은 이유로 남긴다.
--
-- 기존 행은 없다(2026-09-12 실측 saved_trips 0건). 데이터 손실이 없다.
ALTER TABLE saved_trips ALTER COLUMN verdict_at_save DROP NOT NULL;

COMMENT ON COLUMN saved_trips.verdict_at_save IS
  '저장 시점 판정 결과. 2026-09-12 판정 제거로 더 이상 쓰지 않는다(컷 순서 1번 — 컬럼은 남긴다).';
COMMENT ON COLUMN saved_trips.legs IS
  '사용자 조립 코스 구간. 2026-09-12 판정 제거로 더 이상 쓰지 않는다 — 코스는 우리가 짜서 내려준다.';
