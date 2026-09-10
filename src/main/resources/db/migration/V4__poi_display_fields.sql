-- POI 화면 표시용 분류 + 이미지 사용 가부.
--
-- theme·region·category·short_name 은 교통 데이터가 아니라 화면 분류다. 원천은 Figma 02-1이고
-- 지금까지 클라이언트 mockPlan.js 가 들고 있었다. 서버가 목록을 내려주려면 여기 있어야 한다.
-- enum 대신 varchar 를 쓴다 — 분류는 디자인 사정으로 늘어날 수 있고, 마이그레이션은 되돌릴 수 없다.
--
-- Figma 가 분류한 적 없는 POI(여차몽돌해변·덕포해수욕장·도장포어촌체험마을·신선대)는 NULL로 둔다.
-- 없는 분류를 만들지 않는다(절대 규칙 1). 화면은 theme 이 NULL 인 POI 를 목록에서 거른다.
--
-- image_use_ok: TourAPI 이미지를 화면에 쓸 수 있는가.
--   data/seed/pois.json 실측(2026-09-06) — cpyrhtDivCd Type3 은 신선대(129508)·도장포유람선(127182).
--   Type3 는 제3자 저작권이라 이용 조건이 Type1(출처표시)과 다르다. 기준문서 §9 "영문 이미지 사용
--   조건 확인 — 확인 전 사용 보류"와 같은 근거로 false 로 둔다. 조건이 확인되면 UPDATE 한 줄로 켠다.

ALTER TABLE pois
  ADD COLUMN short_name    varchar(50),
  ADD COLUMN theme         varchar(20),
  ADD COLUMN region        varchar(20),
  ADD COLUMN category      varchar(50),
  ADD COLUMN image_use_ok  boolean NOT NULL DEFAULT true;

UPDATE pois p SET
  short_name = v.short_name,
  theme      = v.theme,
  region     = v.region,
  category   = v.category
FROM (VALUES
  ('바람의언덕',         '바람의언덕',   'VIEW',   '남부권', '언덕·전망'),
  ('도장포유람선',       '도장포',       'CRUISE', '남부권', '유람선'),
  ('해금강',             '해금강',       'VIEW',   '남부권', '언덕·전망'),
  ('학동흑진주몽돌해변', '학동',         'BEACH',  '남부권', '해수욕장'),
  ('외도보타니아',       '외도',         'GARDEN', '동부권', '식물원 · 유람선'),
  ('매미성',             '매미성',       'CASTLE', '북부권', '성'),
  ('거제식물원',         '거제식물원',   'GARDEN', '서부권', '식물원')
) AS v(poi_name, short_name, theme, region, category)
WHERE p.poi_name = v.poi_name;

UPDATE pois SET image_use_ok = false
WHERE tour_content_id IN ('127182', '129508');  -- 도장포유람선 · 신선대 (cpyrhtDivCd Type3)
