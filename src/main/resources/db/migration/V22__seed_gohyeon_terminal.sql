-- V22: 고현터미널 — 모든 코스의 출발 지점을 홈 지도에 찍는다 (2026-09-13, 사용자 결정 · Figma 02-2 `501:213`)
--
-- 스팟이 아니다. TourAPI 장소가 아니라서 tour_content_id·theme·region·category 가 없다.
--   · theme 이 NULL 이라 스팟 목록·시간표 탭·코스 지도(theme 로 거름)에는 섞이지 않는다
--   · 홈 지도만 poi_kind = 'TERMINAL' 로 골라 다른 모양(파란 원 + 버스)으로 찍는다
--   · image_use_ok = false — 목록(withImages)·상세가 TourAPI·관광사진 API를 부르지 않게 한다
--
-- 좌표 출처: TAGO(국토교통부) 버스노선정보 getRouteAcctoThrghSttnList, 거제 cityCode 38090,
--   정류소 '터미널(일반)' nodeid GJB500 · nodeno 1101 — gpslati 34.89061475 / gpslong 128.62425069
--   (2026-09-13 수집 원문, geojero 저장소 data/tago/2026-09-13/route_dirs.json).
--   55번 등 BIS 시간표의 '고현'과 코스의 출발지가 이 정류소다(55번 경유 목록의 첫·끝 정류소).
--   컬럼이 numeric(10,7)이라 7자리로 반올림돼 34.8906148 / 128.6242507 로 들어간다(차이 1cm 미만).
-- 추측값이 아니다(절대규칙 1). 건물 좌표가 아니라 **버스를 타는 정류소** 좌표다.

INSERT INTO pois (poi_name, short_name, poi_kind, lat, lng, image_use_ok)
SELECT '고현터미널', '고현터미널', 'TERMINAL'::poi_kind, 34.89061475, 128.62425069, false
WHERE NOT EXISTS (SELECT 1 FROM pois WHERE poi_kind = 'TERMINAL');
