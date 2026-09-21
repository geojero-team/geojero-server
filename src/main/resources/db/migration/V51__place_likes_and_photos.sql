-- V51 — 맛집 · 숙소 · 카페에도 하트와 방문자 사진 (2026-09-22 사용자)
--
-- 사용자: *"숙소/카페/맛집도 스팟과 같이 좋아요 기능 있었으면 좋겠어"* ·
--        *"거제도 스팟처럼 아래 후기 올릴 수 있는 기능도 넣고 싶거든"*
-- 스팟의 규칙을 그대로 옮긴다(디자인브리프 부록 Q · 부록 F, 기준문서 §6):
--   · 하트 — 보기는 비로그인, **누르기만 로그인**. 한 사람이 한 곳에 하나. 수는 셀 때마다 센다(열 없음).
--   · 사진 — 보기는 비로그인, **올리기 · 지우기만 로그인**. 본인 삭제만. 위치정보(EXIF)는 서버가 다시 인코딩해 지운다.
--
-- ⚠️ **사진은 새 테이블을 만들지 않고 visitor_photos 를 넓힌다.** 사진 하나에 id 하나여야
--   `GET /api/visitor-photos/{id}/image` 와 `DELETE /api/visitor-photos/{id}` 가 스팟 · 맛집 구분 없이 그대로 돈다.
--   표를 둘로 나누면 그 두 길도 둘이 되고, 바이트 테이블(visitor_photo_blobs)까지 갈라진다.
--   대신 **둘 중 정확히 하나**만 가리키게 CHECK 로 못박는다 — 둘 다 비거나 둘 다 찬 행은 만들 수 없다.
--
-- 하트는 스팟(spot_likes)과 표를 나눈다 — 가리키는 표가 다르고(pois ↔ places) 키 종류도 다르다(bigint ↔ integer).
-- 한 표에 넣으려면 둘 중 하나가 NULL 인 열이 둘 생기는데, 사진과 달리 하트는 공용 경로가 없어 얻는 것이 없다.

CREATE TABLE place_likes (
  place_id   integer     NOT NULL REFERENCES places(content_id) ON DELETE RESTRICT,
  user_id    bigint      NOT NULL REFERENCES users(user_id)     ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (place_id, user_id)
);
-- 탈퇴 CASCADE 는 user_id 로 찾는다(기본키는 place_id 가 앞이라 못 쓴다) — spot_likes 와 같다
CREATE INDEX idx_place_likes_user ON place_likes (user_id);

COMMENT ON TABLE place_likes IS
  '맛집 · 숙소 · 카페 하트. 한 사람이 한 곳에 하나. 수는 셀 때마다 센다(열 없음) — spot_likes 와 같은 규칙.';
COMMENT ON COLUMN place_likes.created_at IS
  '누른 시각. 화면에 나가지 않는다 — 취소하면 행이 사라지고 다시 누르면 새 시각이다.';

-- ── 방문자 사진을 맛집 · 숙소 · 카페까지 ──
ALTER TABLE visitor_photos ALTER COLUMN poi_id DROP NOT NULL;
ALTER TABLE visitor_photos ADD COLUMN place_id integer REFERENCES places(content_id) ON DELETE RESTRICT;
ALTER TABLE visitor_photos ADD CONSTRAINT chk_visitor_photos_one_target
  CHECK ((poi_id IS NULL) <> (place_id IS NULL));

-- 곳별 목록(최신순) — poi 쪽 인덱스(idx_visitor_photos_poi_created)와 같은 모양
CREATE INDEX idx_visitor_photos_place_created
  ON visitor_photos (place_id, created_at DESC, photo_id DESC);

COMMENT ON COLUMN visitor_photos.poi_id IS
  '스팟에 올린 사진. place_id 와 **둘 중 하나만** 찬다(chk_visitor_photos_one_target).';
COMMENT ON COLUMN visitor_photos.place_id IS
  '맛집 · 숙소 · 카페에 올린 사진(V51). poi_id 와 둘 중 하나만 찬다.';
