-- V21 (2026-09-13): 방문자 사진 — 스팟 상세의 「방문자 사진」(Figma 264:260 · 268:478)
--
-- TourAPI 사진과 섞지 않는다
--   pois 의 사진 필드·TourAPI 캐시와 무관한 별도 테이블이다. TourAPI 사진은 저작권을 사진 한 장
--   단위로 거르는 경로(cpyrhtDivCd)를 타고, 방문자 사진은 사용자가 올린 것이다. 섞으면 둘 다 오염된다.
--
-- 위치정보를 저장하지 않는다
--   서버가 받은 사진을 **다시 인코딩한 JPEG** 만 둔다(photos/VisitorPhotoImages). 원본 바이트와
--   EXIF·XMP(GPS 포함)는 어디에도 저장하지 않는다. 화면이 "사진 속 위치 정보는 저장하지 않아요"
--   (268:529)라고 약속하고, 그 약속은 이 테이블에 들어오는 바이트로 지켜진다.
--
-- 왜 바이트를 별도 테이블에 두는가
--   * 목록 SQL 이 바이트를 실수로 읽을 수 없다(DB 컨테이너 메모리 512M).
--   * users → visitor_photos → visitor_photo_blobs 로 CASCADE 가 이어진다. 계정을 지우면 사진 바이트까지 사라진다.
--   * 나중에 S3 로 옮기면 이 테이블만 걷어내면 된다.
--
-- 제약으로 박지 않은 것
--   * 캡션 길이(200자)는 앱 상수로 검사한다(코드포인트 수). 마이그레이션은 되돌릴 수 없어서 바꿀 수 있는 값을
--     스키마에 박지 않는다(V4 와 같은 이유).
--   * 17곳 제한(theme IS NOT NULL)도 컨트롤러가 검사한다 — theme 은 되살리기 한 줄로 바뀌는 값이다.
--   * 긴 변 1600·픽셀 상한 같은 재인코딩 상수도 CHECK 로 박지 않는다.
--
-- 본인 삭제는 행 삭제다. 지운 사진을 우리가 계속 들고 있지 않는다(숨김 컬럼 없음).

CREATE TABLE visitor_photos (
  photo_id    bigserial PRIMARY KEY,
  poi_id      bigint NOT NULL REFERENCES pois ON DELETE RESTRICT,
  user_id     bigint NOT NULL REFERENCES users ON DELETE CASCADE,
  caption     text,
  width       int NOT NULL CHECK (width > 0),
  height      int NOT NULL CHECK (height > 0),
  byte_size   int NOT NULL CHECK (byte_size > 0),
  created_at  timestamptz NOT NULL DEFAULT now()
);
-- 스팟별 목록(최신순)
CREATE INDEX idx_visitor_photos_poi_created
  ON visitor_photos (poi_id, created_at DESC, photo_id DESC);

COMMENT ON COLUMN visitor_photos.caption IS
  '사용자가 쓴 한 줄. 앞뒤 공백을 지우고 빈 문자열은 NULL. 200자(코드포인트) 상한은 앱이 검사한다.';
COMMENT ON COLUMN visitor_photos.width IS '재인코딩한 뒤의 가로(px). EXIF 방향을 적용한 값이다.';
COMMENT ON COLUMN visitor_photos.height IS '재인코딩한 뒤의 세로(px). EXIF 방향을 적용한 값이다.';
COMMENT ON COLUMN visitor_photos.byte_size IS '재인코딩한 JPEG 의 바이트 수. 용량을 볼 때 SUM 한다.';
COMMENT ON COLUMN visitor_photos.created_at IS
  '올린 시각. 화면의 날짜는 이 값을 KST 로 바꾼 날이다 — EXIF 촬영일이 아니다(EXIF 는 지운다).';

CREATE TABLE visitor_photo_blobs (
  photo_id  bigint PRIMARY KEY REFERENCES visitor_photos ON DELETE CASCADE,
  jpeg      bytea NOT NULL
);

COMMENT ON TABLE visitor_photo_blobs IS
  '재인코딩한 JPEG 바이트. APP0(JFIF) 말고 메타데이터 세그먼트가 없다. 원본은 여기 오지 않는다.';
