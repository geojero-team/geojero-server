-- V43 — 맛집: 음식 사진을 앞으로 · 성포끝집을 더한다(2026-09-19 사용자).
--
-- ① 음식 사진을 앞으로. 사용자: 「음식점이니 만큼 음식 사진이 대부분 차지했으면」. TourAPI 대표 사진은 대개 가게 외관이라
--    카드 · 홈 지도 핀 · 상세 첫 장이 전부 간판이었다. 곳마다 **음식 사진 주소를 등록 순으로** 둔다(V41 · V42 처럼 주소만 —
--    사진 파일은 TourAPI 사진 서버에서 그대로 온다. 자르지 않는다 — 공공누리 3유형).
--    · food_image_urls — 맛집 상세에서 앞에 둘 사진. 서버는 TourAPI 가 **지금 주는** 사진만 순서를 바꾼다(없어진 사진을 되살리지 않는다).
--    · cover_image_url — 목록 사진(카드 · 핀) = 첫 음식 사진. 어방가는 V42 의 첫 가로 사진(가게 안)을 첫 음식 사진으로 바꾼다.
--    음식인지는 TourAPI 사진 이름(imgname 「…_음식_1」 — 쌤김밥 · 웅아물회 · 거제멸치쌈밥 · 장수굴국밥)으로, 이름에 표시가 없는 곳은
--    사진을 보고 갈랐다(차린 음식 · 음식 가까이 — 간판 · 가게 안 · 메뉴판 · 항아리는 음식이 아니다). 순서는 우리가 정하지 않고 등록 순.
--    백만석은 음식 사진이 메뉴 사진(detailImage2 imageYN=N) 칸에만 있다 — 서버가 맛집은 그 칸도 받는다.
--    점순이네밥집은 TourAPI 에 음식 사진이 없다(가게 밖 1 · 안 2, 메뉴 사진 0, 관광사진 API 0) — 비워 둔다(대표 사진 그대로).
--    초정명가는 대표 사진이 이미 음식이라 목록 사진은 그대로다.
-- ② 성포끝집. 점순이네밥집(인기 18위)은 두고 T맵 인기순(V40 과 같은 순위표)에서 **TourAPI 에 음식 사진이 있는 다음 곳**을 더했다 —
--    장수굴국밥(35위) 다음으로 TourAPI 사진이 있는 곳 중 음식 사진이 있는 첫 곳이 성포끝집(50위)이다. 이름 · 좌표는 TourAPI 원문.
--    사이의 곳(배말칼국수김밥 · 거제보재기집 · 만선호해물칼국수 · 금농갈비 · 예이제게장백반 · 싱싱게장 · 보물섬식당 등)은 TourAPI 사진이 0장,
--    거제갈치도 · 거제도굴구이는 TourAPI 와 같은 곳인지 불확실해 V40 에서 뺐다.
ALTER TABLE places ADD COLUMN food_image_urls text[];
ALTER TABLE places ADD CONSTRAINT chk_places_food_images_food_only CHECK (kind = 'FOOD' OR food_image_urls IS NULL);

INSERT INTO places (content_id, kind, sort_order, name, lat, lng, category, grade, booking_url, eng_content_id) VALUES
  (2783397, 'FOOD', 13, '성포끝집', 34.9224143, 128.5256862, NULL, NULL, NULL, NULL);

-- 대박난맛집 — 추가 _1 · _2 · _3 (_4 는 가게 안)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/62/2787162_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/57/2787157_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/60/2787160_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/62/2787162_image2_1.jpg'
 WHERE content_id = 2783696;

-- 강성횟집 — 추가 _1 · _2 · _4 (_3 은 복도)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/85/2753285_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/77/2753277_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/81/2753281_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/85/2753285_image2_1.jpg'
 WHERE content_id = 2753311;

-- 하면옥 — 추가 _1 · _2 · _3 (전부 음식)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/65/2753265_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/62/2753262_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/59/2753259_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/65/2753265_image2_1.jpg'
 WHERE content_id = 2753331;

-- 한꼬막두꼬막 — 추가 _1 (_2 가게 안 · _3 벽)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/39/2753239_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/39/2753239_image2_1.jpg'
 WHERE content_id = 2753321;

-- 쌤김밥 — 사진 이름 「음식」: 추가 _1 · _2 · _3 (_4 「내부」)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/86/2856886_image2_1.JPG',
    'https://tong.visitkorea.or.kr/cms/resource/90/2856890_image2_1.JPG',
    'https://tong.visitkorea.or.kr/cms/resource/87/2856887_image2_1.JPG'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/86/2856886_image2_1.JPG'
 WHERE content_id = 2856903;

-- 어방가 — 추가 _2 · _3 · _4 · _5 (_1 가게 안 — V42 의 목록 사진이었다)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/06/2778406_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/07/2778407_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/08/2778408_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/05/2778405_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/06/2778406_image2_1.jpg'
 WHERE content_id = 2778359;

-- 웅아물회 — 사진 이름 「음식」: 추가 _1 · _2 · _4 (_3 「내부」)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/57/2867357_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/58/2867358_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/56/2867356_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/57/2867357_image2_1.jpg'
 WHERE content_id = 2867399;

-- 거제멸치쌈밥 — 사진 이름 「음식」: 추가 _1 · _2 · _4 (_3 「내부」)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/81/2857981_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/78/2857978_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/82/2857982_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/81/2857981_image2_1.jpg'
 WHERE content_id = 2858010;

-- 백만석 — 메뉴 사진 _1 · _2 · _3 (추가 사진 7장은 가게 안 · 간판 · 메뉴판 · 진열대)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/29/3043329_image2_1.JPG',
    'https://tong.visitkorea.or.kr/cms/resource/28/3043328_image2_1.JPG',
    'https://tong.visitkorea.or.kr/cms/resource/27/3043327_image2_1.JPG'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/29/3043329_image2_1.JPG'
 WHERE content_id = 578976;

-- 초정명가횟집 물회 — 대표 · 추가 _1 · _2 (_3 · _4 항아리 · _5 병). 대표가 이미 음식이라 목록 사진은 그대로
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/62/3491462_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/67/3491467_image2_1.JPG',
    'https://tong.visitkorea.or.kr/cms/resource/66/3491466_image2_1.JPG']
 WHERE content_id = 2858749;

-- 장수굴국밥 — 사진 이름 「음식」: 추가 _1 · _2 · _3 (_4 「내부」)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/31/2857031_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/30/2857030_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/29/2857029_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/31/2857031_image2_1.jpg'
 WHERE content_id = 2857046;

-- 성포끝집 — 추가 _2 · _3 · _4 (_1 가게 안)
UPDATE places SET
  food_image_urls = ARRAY[
    'https://tong.visitkorea.or.kr/cms/resource/86/2784086_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/84/2784084_image2_1.jpg',
    'https://tong.visitkorea.or.kr/cms/resource/83/2784083_image2_1.jpg'],
  cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/86/2784086_image2_1.jpg'
 WHERE content_id = 2783397;
