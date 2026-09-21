-- V49 (2026-09-21): 스팟 하트 — 스팟 목록 「추천순」의 근거 (사용자 결정 2026-09-21 · 디자인브리프 부록 Q)
--
-- 왜
--   스팟 목록의 「추천순」이 무엇을 근거로 줄을 세우는지 화면이 말하지 못했다. 로그인한 사람이 스팟에 하트를
--   누르면 그 수가 순서의 근거가 된다 — 순서를 우리가 정하지 않는다(기준문서 §6 「우리가 고르지 않는다」).
--   동률이면 클라가 9경 번호(pois.nine_scenic_no) 다음에 대표 코스에 든 횟수로 가른다 — 둘 다 이미 있는 값이라
--   여기서는 하트만 만든다.
--
-- 한 사람이 한 스팟에 하트 하나 — (poi_id, user_id) 가 기본키다. 두 번 눌러도 행은 하나다(ON CONFLICT DO NOTHING).
-- 계정을 지우면 그 사람의 하트도 사라진다(ON DELETE CASCADE) — 탈퇴(DELETE /api/me)가 users 한 행만 지우면
-- 되는 규칙(V21 · V33 과 같다). 스팟은 하트가 남아 있으면 지울 수 없다(RESTRICT) — pois 행을 지우는 일은 없고,
-- 있더라도 사람이 눌러 준 것을 조용히 없애지 않는다(방문자 사진 V21 과 같은 방향).
--
-- 제약으로 박지 않은 것
--   * 「화면 스팟 19곳만」(theme IS NOT NULL)은 컨트롤러가 검사한다 — theme 은 되살리기 한 줄로 바뀌는 값이다
--     (V21 이 17곳 제한을 앱에 둔 것과 같은 이유). 고현터미널(TERMINAL)은 사진 칸은 있지만(V22) 스팟 목록에
--     없으므로 하트 대상이 아니다 — 그것도 컨트롤러가 가른다.
--   * 하트 수는 열로 두지 않고 읽을 때마다 센다(count). 열을 두면 행과 열이 어긋날 수 있고, 19곳 × 사용자 수라
--     세는 값이 작다.

CREATE TABLE spot_likes (
  poi_id     bigint      NOT NULL REFERENCES pois(poi_id)   ON DELETE RESTRICT,
  user_id    bigint      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (poi_id, user_id)
);
-- 내 하트 목록 · 탈퇴 CASCADE 는 user_id 로 찾는다(기본키는 poi_id 가 앞이라 못 쓴다)
CREATE INDEX idx_spot_likes_user ON spot_likes (user_id);

COMMENT ON TABLE spot_likes IS
  '스팟 하트. 한 사람이 한 스팟에 하나 — 스팟 목록 「추천순」의 근거. 수는 셀 때마다 센다(열 없음).';
COMMENT ON COLUMN spot_likes.created_at IS
  '누른 시각. 화면에 나가지 않는다 — 취소하면 행이 사라지고 다시 누르면 새 시각이다.';
