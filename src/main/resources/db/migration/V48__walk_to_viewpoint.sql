-- 걸어서 갈 수 없는 스팟의 「대신 걸어갈 곳」 (2026-09-20 사용자 결정)
--
-- 왜: 해금강은 육지가 아니라 **바다 위 바위섬**이다. TourAPI 소개문(contentId 129483)이 직접 적는다 —
--     "거제시 남부면 갈곶리 갈개마을의 남쪽 약 500m 해상에 위치한 바위섬(해발 약 116m, 면적 약 0.1㎢)이다."
--     그 좌표(34.7333 / 128.6839)로 카카오맵 도보 길찾기를 걸면 「도보 길찾기를 이용할 수 없는 지역이에요」가 뜬다.
--     앱이 만드는 길찾기 쌍 35개를 전부 열어 확인했고 **해금강 왕복 2건만** 실패한다(2026-09-20 실측).
--     그리고 화면의 「도보 약 1.1km」는 바다를 건너는 직선이다 — 걸어갈 수 있다고 읽히면 §4 에서 우리가
--     비판한 '할 수 없는 것을 할 수 있는 것처럼 말하기'다. 링크만 고치면 그 말이 남는다.
--
-- 대신 걸어갈 곳을 **우리가 고르지 않았다**(기준문서 §6 「우리가 고르지 않는다」):
--     TourAPI 「거제 우제봉전망대」(contentId 2704694, 남부면 갈곶리 산 2-16)의 소개문이 답을 준다 —
--     "우제봉전망대에 오르면 해금강의 천혜의 비경을 잘 볼 수 있는 모습을 내려다볼 수 있다. …
--      망원경이 있어 해금강 일대를 자세하게 관람할 수 있다. … 해발 107m, 천천히 걸어도 30분이면 도착한다."
--     좌표는 TourAPI mapy/mapx 원문(34.7305356030 / 128.6750254917)을 numeric(10,7) 로 받은 값이다(절대규칙 5).
--     해금강종점에서 카카오맵이 실제로 도보 1.1km · 23분 길을 찾는 것을 확인했다(TourAPI 의 "30분"과 같은 자리).
--
-- 이름은 TourAPI 정식명 「거제 우제봉전망대」를 **줄이기만** 했다(절대규칙 5 · V7 「짧게가 아니라 혼동 없게」).
-- 우제봉전망대를 스팟으로 넣지는 않는다 — 스팟 19곳 선정 기준(기준문서 §6)을 건드리는 일이고,
-- 여기서 필요한 것은 「해금강을 보러 어디까지 걷는가」 하나다.
--
-- 거리는 두지 않는다 — 어느 정류장에서 걷는지에 따라 달라지므로 화면이 두 좌표로 잰다
-- (스팟 ↔ 스팟 걷기와 같은 방식 · 디자인브리프 부록 H 「09-18」).

ALTER TABLE pois ADD COLUMN walk_to_name text;
ALTER TABLE pois ADD COLUMN walk_to_lat  numeric(10,7);
ALTER TABLE pois ADD COLUMN walk_to_lng  numeric(10,7);
-- 왜 스팟 자체로 못 걷는지. 화면이 걷는 칸 아래에 그대로 적는다 — 이유 없이 목적지만 바뀌면
-- 「왜 갑자기 우제봉이지」가 된다(절대규칙 3 — 이유 없는 빈칸을 만들지 않는다).
ALTER TABLE pois ADD COLUMN walk_to_note text;

-- 네 칸은 같이 있거나 같이 없다. 셋만 채우면 화면이 이름 없는 길찾기나 이유 없는 목적지를 그린다.
ALTER TABLE pois ADD CONSTRAINT chk_pois_walk_to_all_or_none CHECK (
  (walk_to_name IS NULL AND walk_to_lat IS NULL AND walk_to_lng IS NULL AND walk_to_note IS NULL)
  OR (walk_to_name IS NOT NULL AND walk_to_lat IS NOT NULL AND walk_to_lng IS NOT NULL AND walk_to_note IS NOT NULL)
);

UPDATE pois SET
  walk_to_name = '우제봉전망대',
  walk_to_lat  = 34.7305356,
  walk_to_lng  = 128.6750255,
  walk_to_note = '해금강은 갈개마을 남쪽 약 500m 해상의 바위섬이에요'
 WHERE short_name = '해금강';
