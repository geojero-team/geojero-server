-- V25 (2026-09-14): 타는 곳 지도 — 스키마만 (값은 V26, geojero jobs/boarding_sql 이 생성)
--
-- 왜 넣는가
--   스팟 시간표가 "55번 13:00"은 말하는데 **어디서 타는지**는 말하지 않았다. 같은 이름의 정류장도
--   방향마다 길 건너편의 다른 정류장이라, 이름만 알려주면 반대편에서 기다리게 된다(사용자 결정 2026-09-13).
--   노선마다 타는 정류장을 지도 핀으로 보여준다.
--
-- 출처
--   좌표·이름: 국토교통부 TAGO 버스노선정보 getRouteAcctoThrghSttnList (거제 38090) 2026-09-13 스냅샷
--   고르는 규칙: geojero jobs/boarding_stops.py — 검토된 이름 · 같은 노선 방향 안에서 타는 곳이 가는 곳보다 앞 · 1500m 안
--   검증: 이름 표 3명 블라인드 매핑 · 거제 BIS/OSM 교차 좌표 · 반박 검증 2회 + 판정(2026-09-14)
--
-- 설계 원칙
--   · 좌표는 원문 글자 그대로(소수 8자리) — numeric(11,8). pois 의 numeric(10,7) 보다 한 자리 넓다
--   · 확신이 없으면 핀을 만들지 않는다: UNRESOLVED(이유 코드) · SPLIT(편마다 타는 쪽이 달라 대표 핀 없음)
--   · 편 단위 예외(32번 고현 20:02 출발 편은 외포·대계를 돌아 길 건너편에 선다)는 boarding_exceptions 에 편마다 둔다
--   · 표에 없는 (구간, 노선) 조합은 API 가 NOT_COLLECTED 로 말한다 — 추측으로 채우지 않는다
--
-- ⚠️ 주의
--   · 시간표(BIS)와 좌표(TAGO)는 다른 원천이다. 둘을 잇는 것은 정류장 이름 표(data/seed/boarding_names.json)다
--   · 화면이 "길 건너편"이라고 말하는 근거는 gap_m(대표 핀과 예외 정류장 사이 m)이다

CREATE TABLE boarding_stops (
  from_poi_id   bigint       NOT NULL REFERENCES pois,
  to_poi_id     bigint       NOT NULL REFERENCES pois,
  route_no      varchar(20)  NOT NULL,
  status        varchar(12)  NOT NULL CHECK (status IN ('RESOLVED','UNRESOLVED','SPLIT')),
  reason_code   varchar(20)  CHECK (reason_code IN ('TOO_FAR','NO_STOP_NAME','WRONG_DIRECTION')),
  tago_route_id varchar(20),
  node_id       varchar(20),
  stop_name     varchar(50),
  lat           numeric(11,8),
  lng           numeric(11,8),
  distance_m    int          CHECK (distance_m >= 0),
  review_note   varchar(500),
  source        varchar(100) NOT NULL,
  PRIMARY KEY (from_poi_id, to_poi_id, route_no),
  CONSTRAINT chk_boarding_pin CHECK ((status = 'RESOLVED') = (node_id IS NOT NULL AND lat IS NOT NULL AND lng IS NOT NULL
                                                             AND stop_name IS NOT NULL AND distance_m IS NOT NULL)),
  CONSTRAINT chk_boarding_reason CHECK ((status = 'UNRESOLVED') = (reason_code IS NOT NULL))
);
COMMENT ON TABLE boarding_stops IS '(출발 곳, 가는 곳, 노선)마다 타는 정류장. 출발·가는 곳은 스팟 또는 고현터미널(poi_kind TERMINAL)';
COMMENT ON COLUMN boarding_stops.status IS 'RESOLVED 핀 있음 · UNRESOLVED 규칙을 통과한 정류장이 없음(reason_code) · SPLIT 편마다 타는 쪽이 달라 대표 핀 없음(예외 편만)';
COMMENT ON COLUMN boarding_stops.reason_code IS 'TOO_FAR 순서는 맞지만 1500m 밖 · NO_STOP_NAME 검토된 이름의 정류장이 경유 목록에 없음 · WRONG_DIRECTION 반대 방향뿐';
COMMENT ON COLUMN boarding_stops.distance_m IS '출발 곳 좌표(스팟은 TourAPI, 고현터미널은 TAGO GJB500)에서 정류장까지 직선거리';
COMMENT ON COLUMN boarding_stops.review_note IS '사람이 판정으로 고친 행의 이유(반박 검증 · 사용자 결정). 규칙 그대로면 NULL';

CREATE TABLE boarding_exceptions (
  from_poi_id bigint        NOT NULL,
  to_poi_id   bigint        NOT NULL,
  route_no    varchar(20)   NOT NULL,
  depart_time time          NOT NULL,
  node_id     varchar(20)   NOT NULL,
  stop_name   varchar(50)   NOT NULL,
  lat         numeric(11,8) NOT NULL,
  lng         numeric(11,8) NOT NULL,
  distance_m  int           NOT NULL CHECK (distance_m >= 0),
  gap_m       int           CHECK (gap_m >= 0),
  note        varchar(500)  NOT NULL,
  PRIMARY KEY (from_poi_id, to_poi_id, route_no, depart_time),
  FOREIGN KEY (from_poi_id, to_poi_id, route_no) REFERENCES boarding_stops ON DELETE CASCADE
);
COMMENT ON TABLE boarding_exceptions IS '대표 핀과 다른 정류장에서 타는 편. depart_time 은 스팟 시간표의 그 편 출발 시각';
COMMENT ON COLUMN boarding_exceptions.gap_m IS '대표 핀 정류장까지 m. SPLIT(대표 핀 없음)이면 NULL';
COMMENT ON COLUMN boarding_exceptions.note IS '그 편의 경로 근거(TAGO 경로 id · 고현 출발 시각) — 화면에는 쓰지 않는다';
