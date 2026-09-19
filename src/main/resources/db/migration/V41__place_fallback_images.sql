-- V41 — 맛집 · 숙소 사진 주소 폴백(2026-09-19 사용자 결정).
--
-- 한화리조트 거제 벨버디어는 국문 사진이 0장이라 영문 TourAPI(3445089) 객실 사진 2장을 쓴다(V40 eng_content_id).
-- 그런데 **운영 서버 키로는 영문 사진이 0장**이다 — 사용자 키로는 2장이 오고, 운영에서 국문 사진은 잘 온다.
-- 운영 키(EC2 TOUR_INFO_KEY)에 영문 관광정보 활용신청이 없는 것으로 보인다(키를 사용자 키로 바꾸면 풀린다 — 사람 작업).
-- 그래서 이 사진들의 **주소만** 둔다: TourAPI 가 사진을 0장 줄 때 쓴다. 사진 파일은 그대로 TourAPI 사진 서버에서 온다(키 불필요).
-- 「TourAPI 는 런타임 호출」 원칙에서 한 곳만 비켜선다 — 주소는 2026-09-19 영문 detailImage2 원문, 등록 순(스위트 오션뷰 _1 이 대표).
ALTER TABLE places ADD COLUMN fallback_image_urls text[];

UPDATE places
   SET fallback_image_urls = ARRAY[
         'https://tong.visitkorea.or.kr/cms/resource/87/4057087_image2_1.jpg',
         'https://tong.visitkorea.or.kr/cms/resource/76/4057076_image2_1.jpg']
 WHERE content_id = 2660777;
