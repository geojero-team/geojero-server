-- 저작권 판단을 POI 단위에서 사진 한 장 단위로 옮긴다.
--
-- V4는 도장포유람선(127182)·신선대(129508)의 **대표 사진**이 cpyrhtDivCd Type3(제3자
-- 저작권)라는 이유로 POI를 통째로 껐다. 그 결과 같은 장소에 딸린 쓸 수 있는 사진
-- (Type1 출처표시)까지 함께 잃었고, 화면에는 이유 없는 회색 카드만 남았다.
--
-- 이제 TourApiClient가 detailCommon2·detailImage2의 cpyrhtDivCd를 **장마다** 읽어
-- Type3만 거른다. Type3뿐인 POI는 여전히 사진이 없지만, 그건 저작권상 맞는 결과다.
--
-- image_use_ok 컬럼은 남긴다 — 자동 판단과 별개로 사람이 특정 POI를 끌 수 있어야 한다.
UPDATE pois SET image_use_ok = true
WHERE tour_content_id IN ('127182', '129508');
