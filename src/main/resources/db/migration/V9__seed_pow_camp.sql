-- 거제도포로수용소유적공원 — 거제 9경 중 유일하게 고현(진입 관문)에 있는 스팟.
--
-- 왜 넣나: 화면 스팟 8곳이 전부 남부·동부·북부·서부 **외곽**이었다. 서울남부·부산사상에서
-- 오는 사람은 전부 고현터미널에 내리는데(진입 하루 118회+), 거기 9경이 하나 있는데도
-- 목록에 없었다. 뚜벅이 서비스의 빈 자리다.
--
-- contentId·좌표는 TourAPI searchKeyword2 실호출로 확인했다(2026-09-10).
--   title 거제도 포로수용소 유적공원 / contentid 127938 / contenttypeid 12
--   mapy 34.8764184192 / mapx 128.6253953896 / addr 경상남도 거제시 계룡로 61
-- 사진은 대표 + 추가 6장이 **전부 cpyrhtDivCd Type1**이라 그대로 쓸 수 있다.
--
-- tier BEST — 기준문서 §2 "최상: 옥포·장승포·고현 시내, 평일 편도 130회+, 10분 간격".
-- 최인접 정류장은 [미확인]이라 여기 넣지 않는다(절대규칙 1). 판정 연결은 그 값이 온 뒤.
INSERT INTO pois (poi_name, poi_kind, tier, tour_content_id, lat, lng,
                  short_name, theme, region, category)
SELECT '거제도포로수용소유적공원', 'SPOT'::poi_kind, 'BEST'::access_tier, '127938',
       34.8764184, 128.6253954,
       '포로수용소', 'HISTORY', '중부권', '유적공원'
WHERE NOT EXISTS (SELECT 1 FROM pois p WHERE p.poi_name = '거제도포로수용소유적공원');
