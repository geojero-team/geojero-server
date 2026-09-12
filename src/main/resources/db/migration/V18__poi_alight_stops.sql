-- V18 (2026-09-12): 스팟의 하차 정류장 — 시간표 조회용과 화면 표시용을 따로 둔다
--
-- 왜 컬럼이 둘인가
--   팀원이 스팟마다 하차 거점을 **사람이 지정**했다(COURSE_METHOD §2). 그런데 시간표에서
--   시각을 찾는 정류장과 사용자가 실제로 내리는 정류장이 다른 경우가 있다 —
--   거제조선해양문화관·거제씨월드는 **신촌**에서 내리지만 시간표에는 신촌 칸이 없어
--   **지세포** 시각을 쓴다. 하나로 합치면 시간표가 빈손이 되거나 화면이 거짓말을 한다.
--     timetable_stop : stops.stop_name 과 맞는 이름. 시간표·출발 조회에 쓴다.
--     alight_label   : "학동 정류장에서 타요" 처럼 사용자에게 보여줄 이름.
--   둘이 다르면 화면이 그 사실을 함께 말해야 한다(절대규칙 3).
--
-- 왜 stops FK 가 아닌가
--   시간표 재조사로 stops 가 전량 교체될 예정이라(기준문서 §9) stop_id 가 바뀌면 FK 가
--   깨진다. 이름으로 두면 교체돼도 살아남고, 이름이 안 맞으면 조회가 빈손이 되어 드러난다.
--
-- timetable_stop 이 NULL 인 스팟은 우리 stops 138행에 그 거점이 없다는 뜻이다 —
-- 시각 칸이 없는 정류장(대금교차로·포로수용소·식물원·옥포대첩기념공원·맹종죽테마파크·
-- 대계·여차)이고, 조회하면 빈 결과가 나온다. 빈 값으로 덮어쓰지 않고 NULL 로 남긴다(절대규칙 1).

ALTER TABLE pois
  ADD COLUMN timetable_stop varchar(50),
  ADD COLUMN alight_label   varchar(50);

COMMENT ON COLUMN pois.timetable_stop IS
  '시간표에서 이 스팟의 시각을 찾을 때 쓰는 정류장 이름. stops.stop_name 과 맞춘다. '
  'NULL 이면 원문 시간표에 그 정류장 칸이 없다 — 조회가 빈 결과가 된다.';
COMMENT ON COLUMN pois.alight_label IS
  '사용자에게 보여줄 하차 정류장 이름("학동 정류장"). timetable_stop 과 다를 수 있다 — '
  '조선해양문화관·씨월드는 신촌에서 내리지만 시간표는 지세포 기준이다.';

UPDATE pois SET timetable_stop = '학동', alight_label = '학동 정류장' WHERE poi_id = 4;   -- 학동흑진주몽돌해변
UPDATE pois SET timetable_stop = '해금강', alight_label = '해금강 정류장' WHERE poi_id = 3;   -- 해금강
UPDATE pois SET timetable_stop = '도장포', alight_label = '도장포 정류장' WHERE poi_id = 1;   -- 바람의언덕
UPDATE pois SET timetable_stop = '도장포', alight_label = '도장포 정류장' WHERE poi_id = 2;   -- 도장포유람선
UPDATE pois SET timetable_stop = '홍포', alight_label = '홍포 또는 여차 종점' WHERE poi_id = 14;   -- 여차,홍포간 해안도로
UPDATE pois SET timetable_stop = '저구', alight_label = '명사 정류장(53·53-1번) 또는 저구 정류장' WHERE poi_id = 12;   -- 명사해수욕장
UPDATE pois SET timetable_stop = NULL, alight_label = '대금교차로 정류장' WHERE poi_id = 7;   -- 매미성  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = NULL, alight_label = '포로수용소 정류장' WHERE poi_id = 13;   -- 거제도포로수용소유적공원  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = NULL, alight_label = '식물원 정류장' WHERE poi_id = 10;   -- 거제식물원  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = '지세포', alight_label = '신촌 정류장' WHERE poi_id = 20;   -- 거제조선해양문화관
UPDATE pois SET timetable_stop = '지세포', alight_label = '신촌 정류장' WHERE poi_id = 18;   -- 거제씨월드
UPDATE pois SET timetable_stop = '능포', alight_label = '능포 정류장' WHERE poi_id = 16;   -- 양지암조각공원
UPDATE pois SET timetable_stop = NULL, alight_label = '옥포대첩기념공원 정류장' WHERE poi_id = 21;   -- 옥포대첩기념공원  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = NULL, alight_label = '맹종죽테마파크 정류장' WHERE poi_id = 17;   -- 거제맹종죽테마공원  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = NULL, alight_label = '대계마을 정류장' WHERE poi_id = 22;   -- 김영삼 전 대통령 생가  ← 시간표에 정류장 칸 없음
UPDATE pois SET timetable_stop = '산방', alight_label = '산방 정류장' WHERE poi_id = 15;   -- 청마기념관
UPDATE pois SET timetable_stop = '거제', alight_label = '거제면 정류장' WHERE poi_id = 19;   -- 거제현 관아

-- 외도보타니아(poi_id 5)는 팀원 산출물에 독립 스팟이 없다 — 「거제해금강/외도」로 해금강에
-- 합쳐 계산했다(배로 가는 곳이라 버스 거점이 해금강과 같다). 그래서 둘 다 NULL 로 남긴다.
