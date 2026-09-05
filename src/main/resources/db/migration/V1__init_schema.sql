
CREATE TYPE route_type AS ENUM ('CITY','INTERCITY','FERRY');
CREATE TYPE stop_status AS ENUM ('TIME','SKIP','EMPTY','TEXT');
CREATE TYPE time_kind AS ENUM ('LITERAL','CACHED_FORMULA','ANNOTATION');
CREATE TYPE alert_kind AS ENUM ('SUSPENSION','DETOUR','CLOSURE');
CREATE TYPE poi_kind AS ENUM ('SPOT','FOOD','FERRY_DOCK','TERMINAL');
CREATE TYPE access_tier AS ENUM ('BEST','GOOD','FAIR','POOR');
CREATE TYPE course_theme AS ENUM ('NATURE','HISTORY','ISLAND','FOOD');
CREATE TYPE auth_provider AS ENUM ('KAKAO');
CREATE TYPE saved_trip_status AS ENUM ('PLANNED','ONGOING','DONE');
CREATE TYPE lang_code AS ENUM ('EN','JA');
CREATE TYPE match_source AS ENUM ('AUTO_CANDIDATE','HUMAN');

CREATE TABLE timetable_versions (
  version_id  bigserial PRIMARY KEY,
  valid_from  date NOT NULL,
  valid_to    date,
  based_on    date NOT NULL,
  source_file varchar(255) NOT NULL,
  source_url  varchar(500),
  fetched_at  timestamptz NOT NULL,
  note        varchar(500)
);
CREATE INDEX idx_versions_valid ON timetable_versions (valid_from, valid_to);

CREATE TABLE routes (
  route_id   bigserial PRIMARY KEY,
  route_no   varchar(20) NOT NULL,
  route_name varchar(100) NOT NULL,
  route_type route_type NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (route_no, route_type)
);

CREATE TABLE stops (
  stop_id         bigserial PRIMARY KEY,
  stop_name       varchar(100) NOT NULL UNIQUE,
  bis_stop_id     varchar(30),
  lat             numeric(10,7),
  lng             numeric(10,7),
  is_anchor_point boolean NOT NULL DEFAULT false
);

CREATE TABLE trips (
  trip_id      bigserial PRIMARY KEY,
  version_id   bigint NOT NULL REFERENCES timetable_versions ON DELETE CASCADE,
  route_id     bigint NOT NULL REFERENCES routes ON DELETE RESTRICT,
  direction    smallint NOT NULL,
  runs_weekday boolean NOT NULL,
  runs_holiday boolean NOT NULL,
  headsign_raw text,
  note_raw     text,
  source_sheet varchar(100) NOT NULL,
  source_row   int NOT NULL
);
CREATE INDEX idx_trips_lookup ON trips (version_id, route_id, direction);

CREATE TABLE trip_stops (
  trip_stop_id bigserial PRIMARY KEY,
  trip_id      bigint NOT NULL REFERENCES trips ON DELETE CASCADE,
  seq          smallint NOT NULL,
  stop_id      bigint NOT NULL REFERENCES stops ON DELETE RESTRICT,
  status       stop_status NOT NULL,
  depart_min   int,
  raw_text     varchar(200) NOT NULL,
  time_kind    time_kind,
  source_cell  varchar(30) NOT NULL,
  UNIQUE (trip_id, seq)
);
CREATE INDEX idx_trip_stops_stop ON trip_stops (stop_id);

CREATE TABLE pois (
  poi_id              bigserial PRIMARY KEY,
  tour_content_id     varchar(30) UNIQUE,
  poi_name            varchar(100) NOT NULL,
  poi_kind            poi_kind NOT NULL,
  lat                 numeric(10,7),
  lng                 numeric(10,7),
  intro_text          varchar(500),
  open_time           time,
  close_time          time,
  closed_days         varchar(50),
  stay_min            int NOT NULL DEFAULT 60,
  last_departure_time time,
  check_url           varchar(500),
  booking_url         varchar(500),
  last_checked_at     timestamptz,
  tier                access_tier,
  nearest_stop_id     bigint REFERENCES stops ON DELETE SET NULL,
  walk_min_from_stop  int
);

CREATE TABLE poi_i18n (
  poi_i18n_id     bigserial PRIMARY KEY,
  poi_id          bigint NOT NULL REFERENCES pois ON DELETE CASCADE,
  lang            lang_code NOT NULL,
  tour_content_id varchar(30) NOT NULL,
  content_type_id varchar(4) NOT NULL,
  copyright_div   varchar(10),
  matched_by      match_source NOT NULL,
  matched_at      timestamptz,
  UNIQUE (poi_id, lang)
);

CREATE TABLE service_alerts (
  alert_id   bigserial PRIMARY KEY,
  kind       alert_kind NOT NULL,
  route_id   bigint REFERENCES routes ON DELETE CASCADE,
  poi_id     bigint REFERENCES pois ON DELETE CASCADE,
  stop_id    bigint REFERENCES stops ON DELETE CASCADE,
  date_from  date NOT NULL,
  date_to    date,
  reason     varchar(200) NOT NULL,
  source_url varchar(500),
  fetched_at timestamptz NOT NULL
);
CREATE INDEX idx_alerts_range ON service_alerts (date_from, date_to);

CREATE TABLE holidays (
  holiday_date date PRIMARY KEY,
  holiday_name varchar(50) NOT NULL
);

CREATE TABLE courses (
  course_id     bigserial PRIMARY KEY,
  course_name   varchar(100) NOT NULL,
  summary       varchar(300),
  theme         course_theme NOT NULL,
  thumbnail_url varchar(500),
  enabled       boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE course_pois (
  course_poi_id bigserial PRIMARY KEY,
  course_id     bigint NOT NULL REFERENCES courses ON DELETE CASCADE,
  poi_id        bigint NOT NULL REFERENCES pois ON DELETE RESTRICT,
  poi_seq       int NOT NULL,
  is_fixed      boolean NOT NULL DEFAULT false,
  UNIQUE (course_id, poi_seq)
);

CREATE TABLE users (
  user_id    bigserial PRIMARY KEY,
  provider   auth_provider NOT NULL,
  oauth_id   varchar(100) NOT NULL,
  nickname   varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, oauth_id)
);

CREATE TABLE saved_trips (
  saved_trip_id bigserial PRIMARY KEY,
  user_id       bigint NOT NULL REFERENCES users ON DELETE CASCADE,
  course_id     bigint NOT NULL REFERENCES courses ON DELETE RESTRICT,
  travel_date   date NOT NULL,
  arrival_time  time NOT NULL,
  return_time   time NOT NULL,
  verdict_at_save jsonb NOT NULL,
  status        saved_trip_status NOT NULL DEFAULT 'PLANNED',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_saved_user_date ON saved_trips (user_id, travel_date);
