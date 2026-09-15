-- V30 (2026-09-15): 거제 9경 7경 「공곶이와 내도」 · 8경 「동백섬 지심도」를 화면 스팟으로 넣는다 (사용자 결정)
--
-- V28 주석은 두 곳을 "배편이 §3 에 없어 스팟이 아니다"라고 뺐다. 2026-09-15 사용자가 넣기로 했다 —
-- 시간표는 비워 두고(도선 시각은 나중에 채움), 스팟 목록 · 시간표 탭 · 스팟 상세 · 9경 목록에는 나오게 한다.
--   · timetable_stop · alight_label 없음, ferry_links 없음 → 시간표 API 가 emptyReason TIMETABLE_PENDING 을 준다.
--     외도 유람선 선착장(ferry_docks 4곳)에 잇지 않는다 — 내도 · 지심도 도선은 운항사가 달라 외도 배 시각이 잘못 붙는다.
--   · tier NULL — 내릴 곳을 모르는데 접근 난이도를 단정하지 않는다(V14 와 같은 이유).
--
-- 정본은 TourAPI 다(절대규칙 5). 2026-09-15 searchKeyword2 + detailCommon2 + detailImage2 실호출로 확인했다.
--   7경 「공곶이와 내도」 → **스팟 하나**(사용자 결정). 대표 contentId 는 공곶이 2536196 으로 잡는다.
--     · 공곶이 2536196: 소개문이 "거제시가 지정한 거제 9경 중 한 곳"이라 적는다. 대표 사진은 없지만 detailImage2 12장이 전부 Type1
--     · 내도 578459: 사진이 전부 Type3(대표 · 추가 2장), 관광사진 API 에 거제 내도 0건 — 대표로 잡으면 사진이 없다
--     좌표는 공곶이(mapy 34.7945463817824 / mapx 128.713902310567). 내도는 남쪽 약 0.9km(34.7867355868 / 128.7138261918).
--     poi_name 은 TourAPI 등록명 「공곶이」, 화면 이름(short_name)은 「공곶이·내도」 — 9경 이름을 줄이기만 한다(V7 규칙).
--     photo_keyword 「공곶이」 — 관광사진 API 「공곶이의 노란바다」 1장(목록 카드 · 지도 핀용, 대표 사진이 없어서).
--   8경 「동백섬 지심도」 → 지심도 128035(일운면 지심도길 31-2, mapy 34.8179993905 / mapx 128.7485334584).
--     대표 사진이 Type3 이고 detailImage2 0장이라, 관광사진 API 「지심도(동백섬)」(거제 50장)를 photo_keyword 로 쓴다.
--     「동백섬 지심도터미널」(2756617, 장승포) · 「지심도선착장」(2784327)은 배 타는 곳이라 스팟이 아니다.
--
-- 분류: theme CRUISE(섬·유람선, V29) · region 동부권(일운면 — 외도보타니아와 같은 권역) · category 는 TourAPI 소개문의 말
--   (공곶이 「계단식 다랭이 농원」 · 내도 · 지심도 「섬」).
-- summary 는 Claude 초안(2026-09-15 · V29 와 같은 규칙) — 근거는 위 두 · 세 곳의 TourAPI 원문뿐이다. 내도에 배로 간다는 말은
--   원문에 없어 넣지 않았다. 지심도의 도선 약 15분 · 탐방 2시간 이내는 원문 그대로다.
-- 좌표는 numeric(10,7)에 맞춰 7자리로 반올림된다(V3 주석).

INSERT INTO pois (poi_name, poi_kind, tour_content_id, lat, lng,
                  short_name, theme, region, category, photo_keyword,
                  nine_scenic_no, summary, image_use_ok)
SELECT v.poi_name, 'SPOT'::poi_kind, v.cid, v.lat, v.lng,
       v.short_name, v.theme, v.region, v.category, v.photo_keyword,
       v.nine_no, v.summary, true
FROM (VALUES
  ('공곶이', '2536196', 34.7945463817824, 128.713902310567,
   '공곶이·내도', 'CRUISE', '동부권', '농원 · 섬', '공곶이', 7,
   '예구마을에서 숲길을 20분쯤 걸으면 나오는 계단식 농원 공곶이와, 가까운 섬 내도를 묶은 거제 9경입니다. 공곶이에는 수선화·동백 등 50여 종의 꽃과 나무가 자라고, 겨울에 수선화가 만개합니다. 내도에는 동백나무 숲길과 외도·해금강을 한곳에서 보는 전망대가 있습니다.'),
  ('지심도', '128035', 34.8179993905, 128.7485334584,
   '지심도', 'CRUISE', '동부권', '섬', '지심도', 8,
   '하늘에서 본 모양이 마음 심(心) 자를 닮은 작은 섬으로, 동백나무가 많아 ‘동백섬’이라 불립니다. 장승포항과 지세포항에서 도선으로 약 15분 걸리고, 둘러보는 데 2시간이면 충분합니다.')
) AS v(poi_name, cid, lat, lng, short_name, theme, region, category, photo_keyword, nine_no, summary)
WHERE NOT EXISTS (SELECT 1 FROM pois p WHERE p.tour_content_id = v.cid);
