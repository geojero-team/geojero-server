-- V23 (2026-09-14): 유람선 시간표 — 스키마만 (값은 V24, geojero jobs/ferry_run 이 생성)
--
-- 왜 넣는가
--   2026-09-13 사용자 결정으로 유람선 시간표를 버스처럼 넣는다(기준문서 §6 기술 메모 「유람선 시간표」).
--   전에는 "외도유람선은 매일 시각이 달라 박제 불가 → 당일 확인 링크만"이었는데, 운항사 사이트가
--   **월마다 날짜별 시간표**를 공개하고 있었다. 외도보타니아는 배로만 가므로 이 표가 없으면 가는 방법을 말할 수 없다.
--
-- 출처
--   기준: 외도유람선 예약센터 https://oedoticket.com/page/time-schedule.php (운항사 4곳)
--   대조: 도장포유람선 누리집 https://www.dojangpo.kr/page/time-schedule.php (두 사이트에 다 있는 도장포 편은 전부 같아야 넣는다)
--   코스 사실(코스명·주소·총 소요시간·외도 체류): 예약센터 상품 페이지 view.php?cid=… 7개
--
-- 설계 원칙
--   · 버스 trips/routes 에 넣지 않는다 — 스냅샷(SnapshotRepository)이 모든 trip 을 싣고 SpotLayer·/api/stops 로 새며,
--     runs_weekday/holiday 로는 날짜별 달력을 못 담는다. 배는 요일 구분이 없고 날짜마다 원문이 있다.
--   · 수집(fetch) 단위로 쌓고 지우지 않는다. 같은 날짜가 다음 수집에 또 나오면 **가장 늦은 수집이 이긴다**(API).
--   · "운행 없음"은 원문이 그 날짜를 공개했을 때만 말할 수 있다 → 공개 범위(ferry_coverage)를 따로 둔다.
--     공개 범위는 **그 선착장이 그 달 블록에서 실제로 편을 가진 첫날~마지막 날**이다. 그 밖은 화면에서 「시각 미확인」.
--   · 복귀 시각은 저장하지 않는다 — 출항 + 총 소요시간으로 API 가 계산하고 늘 「약」을 붙인다(원문: 10~30분 조기·지연 출항).
--   · 좌표를 저장하지 않는다. 선착장은 지도에 찍지 않는다(와현·장승포·지세포는 pois 행도 없다).
--
-- ⚠️ 주의
--   · 외도에 가는 배는 외도상륙 코스뿐이다. 모든 배는 **출발한 선착장으로 돌아오는 왕복**이다(외도 → 다른 항 편도 없음).
--   · 11월은 2026-09-14 현재 미공개다(도장포 누리집: "11월 예약시간은 10월 10일경 확인 바랍니다"). 행이 없는 것이 사실이다.

CREATE TABLE ferry_docks (
  dock_id       smallserial  PRIMARY KEY,
  dock_code     varchar(20)  NOT NULL UNIQUE,
  operator_name varchar(50)  NOT NULL,
  short_name    varchar(20)  NOT NULL,
  address       varchar(200) NOT NULL,
  seq           smallint     NOT NULL
);
COMMENT ON TABLE ferry_docks IS '선착장 = 운항사. 원천(예약센터)에서 운항사 하나가 선착장 하나다.';
COMMENT ON COLUMN ferry_docks.dock_code IS 'DOJANGPO | WAHYEON | JANGSEUNGPO | JISEPO — 파이프라인 상수. 생성 마이그레이션이 id 대신 이것으로 잇는다';
COMMENT ON COLUMN ferry_docks.operator_name IS '시간표 원문의 운항사명(div.name) 그대로';
COMMENT ON COLUMN ferry_docks.short_name IS '칩에 쓰는 이름. 원문명을 줄이기만 한다(V7 규칙) — 도장포유람선 → 도장포';
COMMENT ON COLUMN ferry_docks.address IS '상품 페이지 주소 원문';
COMMENT ON COLUMN ferry_docks.seq IS '칩 순서 — 기준문서 §6 나열 순(도장포·와현·장승포·지세포)';

CREATE TABLE ferry_courses (
  course_id        serial       PRIMARY KEY,
  dock_id          smallint     NOT NULL REFERENCES ferry_docks,
  legend_colour    varchar(7)   NOT NULL,
  legend_label     varchar(100) NOT NULL,
  course_name      varchar(200) NOT NULL,
  lands_on_oedo    boolean      NOT NULL,
  oedo_stay_min    int          CHECK (oedo_stay_min > 0),
  total_min        int          NOT NULL CHECK (total_min > 0),
  total_text       varchar(30)  NOT NULL,
  product_cid      varchar(80)  NOT NULL UNIQUE,
  booking_url      varchar(500) NOT NULL,
  facts_fetched_at timestamptz  NOT NULL,
  UNIQUE (dock_id, legend_colour),
  CONSTRAINT chk_stay_iff_landing CHECK (lands_on_oedo = (oedo_stay_min IS NOT NULL))
);
COMMENT ON TABLE ferry_courses IS '코스 = 선착장 × 시간표 원문 색. 예약센터 상품(cid) 하나와 1:1. 예약 링크는 편마다가 아니라 선착장 × 코스마다 하나다';
COMMENT ON COLUMN ferry_courses.legend_colour IS '시간표 span 색 원문 — #000000 외도상륙+해금강선상관광 / #ff0000 해금강선상관광(외도 상륙 X)';
COMMENT ON COLUMN ferry_courses.course_name IS '상품 페이지 h2 원문(「외도입장료 별도」 같은 문구 포함). 화면 각주에 그대로 쓴다';
COMMENT ON COLUMN ferry_courses.oedo_stay_min IS '상품 본문 「외도보타니아 상륙관광 (2시간)」 → 120. 상륙 코스만 값이 있다';
COMMENT ON COLUMN ferry_courses.total_min IS '상품 본문 「총 소요시간 : 약 N시간 M분」 — 출발 선착장 출항부터 같은 선착장 복귀까지';
COMMENT ON COLUMN ferry_courses.total_text IS '원문 표기 그대로(「약 2시간 40분」)';

CREATE TABLE ferry_coverage (
  coverage_id     serial       PRIMARY KEY,
  dock_id         smallint     NOT NULL REFERENCES ferry_docks,
  month           date         NOT NULL,
  covered_from    date         NOT NULL,
  covered_to      date         NOT NULL,
  fetched_at      timestamptz  NOT NULL,
  source_url      varchar(500) NOT NULL,
  cross_check_url varchar(500),
  raw_snapshot    varchar(255) NOT NULL,
  UNIQUE (dock_id, month, fetched_at),
  CHECK (month = date_trunc('month', month)::date),
  CHECK (covered_from <= covered_to),
  CHECK (date_trunc('month', covered_from)::date = month AND date_trunc('month', covered_to)::date = month)
);
COMMENT ON TABLE ferry_coverage IS '공개 범위 = 선착장 × 달 × 수집. 이 범위 안의 날짜만 「운행 있음/예정된 배 없음」을 말할 수 있다. 밖은 「시각 미확인」';
COMMENT ON COLUMN ferry_coverage.covered_from IS '그 선착장이 그 달 블록에서 편을 가진 첫날(수집일 이전 날짜는 원문에 없다)';
COMMENT ON COLUMN ferry_coverage.covered_to IS '그 선착장이 그 달 블록에서 편을 가진 마지막 날. 뒤쪽이 비면 「운행 없음」이 아니라 「시각 미확인」으로 떨어진다';
COMMENT ON COLUMN ferry_coverage.fetched_at IS '시간표 페이지 수집 시각(KST). 한 번 돌리면 4곳이 같은 값';
COMMENT ON COLUMN ferry_coverage.cross_check_url IS '도장포만 — 도장포유람선 누리집과 편 단위로 대조해 전부 일치한 경우에만 들어온다';
COMMENT ON COLUMN ferry_coverage.raw_snapshot IS '원문 보관 위치(geojero 저장소 data/ferry/<수집일>/…)';

CREATE TABLE ferry_sailings (
  sailing_id  bigserial PRIMARY KEY,
  coverage_id int       NOT NULL REFERENCES ferry_coverage ON DELETE CASCADE,
  course_id   int       NOT NULL REFERENCES ferry_courses,
  sail_date   date      NOT NULL,
  depart_time time      NOT NULL,
  UNIQUE (coverage_id, course_id, sail_date, depart_time)
);
CREATE INDEX ix_ferry_sailings_date ON ferry_sailings (sail_date);
COMMENT ON TABLE ferry_sailings IS '편 = 출항 날짜·시각. 외도 도착·출발 시각은 원문에 없어 만들지 않는다';

CREATE TABLE ferry_links (
  poi_id       bigint       NOT NULL REFERENCES pois,
  dock_id      smallint     NOT NULL REFERENCES ferry_docks,
  relation     varchar(20)  NOT NULL CHECK (relation IN ('DESTINATION','DOCK','NEAR_DOCK')),
  source_quote varchar(300) NOT NULL,
  source_url   varchar(500) NOT NULL,
  PRIMARY KEY (poi_id, dock_id)
);
COMMENT ON TABLE ferry_links IS '스팟 ↔ 선착장. 상품 페이지에 근거 문장이 있는 것만 둔다(사람이 검토한 매핑). 없는 조합을 추측으로 잇지 않는다';
COMMENT ON COLUMN ferry_links.relation IS 'DESTINATION 그 선착장 배로 가는 곳(외도보타니아) · DOCK 스팟이 곧 선착장(도장포유람선) · NEAR_DOCK 선착장 근처라는 원문이 있다(바람의언덕 「도보 1분거리」)';
COMMENT ON COLUMN ferry_links.source_quote IS '상품 페이지 원문 문장. 도보 시간 같은 숫자는 인용으로만 보낸다(기준문서 §9 도보 시간 [미확인])';
