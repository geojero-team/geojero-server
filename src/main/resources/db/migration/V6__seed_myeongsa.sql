-- 명사해수욕장 — 화면 목록에는 있는데 시드에 없어서 사진도 소개도 못 받던 POI.
--
-- contentId·좌표는 TourAPI searchKeyword2 실호출로 확인했다(2026-09-10,
-- lDongRegnCd=48 / lDongSignguCd=310). 추측한 값이 아니다(절대규칙 1).
--   title 명사해수욕장 / contentid 126577 / mapy 34.7272513743 / mapx 128.6047816783
--   addr 경상남도 거제시 남부면 명사해수욕장길
-- lat·lng는 numeric(10,7)이라 소수 일곱 자리로 맞췄다.
--
-- tier POOR — 기준문서 §2 "최하: 홍포·명사". 53·53-1이 도로 유실로 명사해수욕장앞을
-- 우회 중이라 코스로는 불성립이지만, 스팟으로는 존재하고 화면에도 이미 나온다.
INSERT INTO pois (poi_name, poi_kind, tier, tour_content_id, lat, lng,
                  short_name, theme, region, category)
SELECT '명사해수욕장', 'SPOT'::poi_kind, 'POOR'::access_tier, '126577',
       34.7272514, 128.6047817,
       '명사해수욕장', 'BEACH', '남부권', '해수욕장'
WHERE NOT EXISTS (SELECT 1 FROM pois p WHERE p.poi_name = '명사해수욕장');
