-- V33 (2026-09-16): 방문자 사진 신고 — 부적절한 사진을 이용자가 그 자리에서 내릴 수 있는 수단.
--
-- 왜 지금 만드는가: 원스토어 등재 설문이 「이용자가 만든 콘텐츠를 신고할 수단이 있는가」를 묻는다.
-- 사진을 공개로 받으면서 내릴 방법이 없으면 심사에서 걸린다(사용자 결정 2026-09-16).
--
-- **지우지 않고 감춘다.** 잘못된 신고였을 때 되돌릴 수 있어야 하고, 남이 올린 사진을 다른 사람이
-- 영구히 없앨 수 있으면 안 된다. 되돌리려면 hidden_at 을 NULL 로 되돌린다.
-- 감춰진 사진은 목록에도 이미지 경로에도 나오지 않는다(VisitorPhotoController).

ALTER TABLE visitor_photos ADD COLUMN hidden_at timestamptz;

COMMENT ON COLUMN visitor_photos.hidden_at IS
  '신고로 감춰진 시각. NULL 이면 공개. 되돌리려면 NULL 로 되돌린다(행은 지우지 않는다).';

-- 누가 어떤 사진을 신고했는지. 같은 사람이 같은 사진을 두 번 세지 않게 (photo_id, user_id) 가 기본키다.
-- 사진이나 계정이 사라지면 함께 사라진다 — 탈퇴(DELETE /api/me)가 users 한 행만 지우면 되는 규칙을 지킨다.
CREATE TABLE visitor_photo_reports (
  photo_id   bigint NOT NULL REFERENCES visitor_photos ON DELETE CASCADE,
  user_id    bigint NOT NULL REFERENCES users ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (photo_id, user_id)
);

COMMENT ON TABLE visitor_photo_reports IS
  '방문자 사진 신고 기록. 신고 한 건이면 그 사진을 바로 감춘다(visitor_photos.hidden_at).';
