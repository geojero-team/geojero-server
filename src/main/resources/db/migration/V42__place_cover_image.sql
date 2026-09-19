-- V42 — 맛집 · 숙소 목록 사진(카드 · 홈 지도 핀) 규칙 하나(2026-09-19 사용자).
--
-- 목록 사진은 TourAPI 대표 사진(firstimage)이다. 그런데 **대표 사진이 세로**면 핀(높이 28, 폭은 사진 비율)이 혼자 좁고
-- 카드(3:2)에는 양옆이 빈다. 그런 곳만 **TourAPI 추가 사진(detailImage2) 중 등록 순 첫 가로 사진**을 목록 사진으로 쓴다.
-- 우리가 고르는 게 아니라 등록 순서를 따른다. 상세 화면의 사진 순서(대표 사진이 첫 장)는 그대로다.
-- V41 처럼 **주소만** 둔다 — 사진 파일은 TourAPI 사진 서버에서 그대로 온다. 사진은 자르지 않는다(공공누리 3유형).
-- 2026-09-19 확인: 19곳 중 대표 사진이 세로인 곳은 어방가 하나(705 × 940). 추가 사진 5장은 전부 가로, 등록 순 첫 장이 2778404(940 × 705).
ALTER TABLE places ADD COLUMN cover_image_url text;

UPDATE places
   SET cover_image_url = 'https://tong.visitkorea.or.kr/cms/resource/04/2778404_image2_1.jpg'
 WHERE content_id = 2778359;
