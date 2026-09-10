-- POI 시드 — data/seed/pois.json (기준문서 §2 티어표·§3 + TourAPI searchKeyword2 실측 2026-09-06)
--
-- 왜 마이그레이션인가: 운영 DB에 직접 접근할 수 없는 동안에도 배포로 데이터가 도달해야 한다.
-- jobs/load_seed.py와 같은 키(poi_name)로 판단하므로 나중에 파이프라인을 돌려도 중복되지 않고
-- 같은 행이 갱신된다. poi_id가 밀리면 API 참조가 깨지므로 삭제·재삽입은 하지 않는다.
--
-- 좌표: TourAPI mapx(경도)→lng, mapy(위도)→lat. numeric(10,7)에 맞춰 7자리 반올림.
-- 덕포해수욕장: 국문 contentid 미확보 → intro_text 폴백 경로 (절대 규칙 1 — 없는 값을 만들지 않는다).
-- 거제식물원·신선대: tier가 기준문서 티어표에 없음 → NULL.
-- 유람선은 도장포유람선 127182(dojangpo.kr, §3 막배 15:30의 주체) 채택.

INSERT INTO pois (poi_name, poi_kind, tier, tour_content_id, lat, lng,
                  last_departure_time, check_url)
SELECT v.poi_name, v.poi_kind::poi_kind, v.tier::access_tier, v.tour_content_id,
       v.lat, v.lng, v.last_departure_time::time, v.check_url
FROM (VALUES
  ('바람의언덕',         'SPOT',       'POOR', '129479',  34.7440458, 128.6633111, NULL,    NULL),
  ('도장포유람선',       'FERRY_DOCK', 'POOR', '127182',  34.7421508, 128.6626096, '15:30', 'https://www.dojangpo.kr'),
  ('해금강',             'SPOT',       'POOR', '126224',  34.7333000, 128.6839000, NULL,    NULL),
  ('학동흑진주몽돌해변', 'SPOT',       'POOR', '127658',  34.7747520, 128.6414980, NULL,    NULL),
  ('외도보타니아',       'SPOT',       'POOR', '126581',  34.7694723, 128.7113921, NULL,    'https://oedocruise.com/cruiseinfo/course/'),
  ('여차몽돌해변',       'SPOT',       'POOR', '126579',  34.7138000, 128.6262000, NULL,    NULL),
  ('매미성',             'SPOT',       'FAIR', '2536133', 34.9682131, 128.7050934, NULL,    NULL),
  ('덕포해수욕장',       'SPOT',       'FAIR', NULL,      NULL,       NULL,        NULL,    NULL),
  ('도장포어촌체험마을', 'SPOT',       'POOR', '128613',  34.7417175, 128.6638039, NULL,    NULL),
  ('거제식물원',         'SPOT',       NULL,   '2648073', 34.8568211, 128.5780987, NULL,    NULL),
  ('신선대',             'SPOT',       NULL,   '129508',  34.7381373, 128.6627236, NULL,    NULL)
) AS v(poi_name, poi_kind, tier, tour_content_id, lat, lng, last_departure_time, check_url)
WHERE NOT EXISTS (SELECT 1 FROM pois p WHERE p.poi_name = v.poi_name);
