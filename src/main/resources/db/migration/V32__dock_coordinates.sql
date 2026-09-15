-- V32 (2026-09-15): 선착장 좌표 — 배 스팟 시간표의 「타는 곳」 지도 카드 (사용자 결정)
--
-- V23 은 "선착장은 지도에 찍지 않는다"며 좌표를 두지 않았다. 2026-09-15 사용자가 버스 칩처럼 배 칩도 타는 곳을
-- 카카오맵으로 보이자고 했다(외도보타니아 · 도장포유람선 · 공곶이·내도 · 지심도).
--
-- 좌표 출처 — 추측값이 아니다(절대규칙 1):
--   · TourAPI 등록 좌표(정본, 절대규칙 5)
--       도장포       127182 「도장포유람선」         34.7421507509 / 128.6626095963
--       와현         2776287 「외도유람선 외도랑」   34.8119048360 / 128.7047846427  — 주소 와현해변길 46 이 와현 선착장 주소(V24)와 같다
--       지심도 터미널 2756617 「동백섬 지심도터미널」 34.8673157274 / 128.7276450720
--   · TourAPI 에 없는 곳은 운항사 공개 주소를 카카오 주소 검색(Maps JS Geocoder, 2026-09-15)으로 옮겼다
--       장승포  「경남 거제시 장승로 138」(V24 장승포유람선)          34.8663080291 / 128.7246407484
--       지세포  「경남 거제시 일운면 지세포해안로 89-19」(V24 지세포관광유람선) 34.8314074543 / 128.7036132168
--       구조라  「경상남도 거제시 일운면 구조라로 21」(V31 도선(구조라))   34.8063338734 / 128.6955461173
--   교차 확인: TourAPI 가 있는 세 곳은 같은 주소의 카카오 주소 검색값과 4~15m 차이다.
-- 컬럼이 numeric(10,7)이라 7자리로 반올림된다(V3 주석).

ALTER TABLE ferry_docks ADD COLUMN lat numeric(10,7), ADD COLUMN lng numeric(10,7);
COMMENT ON COLUMN ferry_docks.lat IS '선착장 좌표(V32) — TourAPI 등록값, 없으면 운항사 주소의 카카오 주소 검색값. 타는 곳 지도 카드용';

UPDATE ferry_docks d SET lat = v.lat, lng = v.lng
FROM (VALUES
  ('DOJANGPO',    34.7421507509, 128.6626095963),
  ('WAHYEON',     34.8119048360, 128.7047846427),
  ('JANGSEUNGPO', 34.8663080291, 128.7246407484),
  ('JISEPO',      34.8314074543, 128.7036132168)
) AS v(code, lat, lng)
WHERE d.dock_code = v.code;

ALTER TABLE shuttle_docks ADD COLUMN lat numeric(10,7), ADD COLUMN lng numeric(10,7);
COMMENT ON COLUMN shuttle_docks.lat IS '도선 선착장 좌표(V32) — 출처는 ferry_docks.lat 과 같은 규칙';

UPDATE shuttle_docks s SET lat = v.lat, lng = v.lng
FROM (VALUES
  ('구조라', 34.8063338734, 128.6955461173),
  ('장승포', 34.8673157274, 128.7276450720)
) AS v(dock, lat, lng)
WHERE s.dock_name = v.dock;
