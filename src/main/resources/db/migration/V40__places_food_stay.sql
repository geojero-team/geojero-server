-- V40 — 맛집 12 · 숙소 7 (2026-09-19 사용자 결정, geojero 기준문서 §6 「맛집 · 숙소」).
--
-- 우리가 고르지 않았다: 순서(sort_order)는 「관광지별 연관 관광지 정보」(T맵 내비 목적지, 2025-09 ~ 2026-08 합산)의 인기순이고,
-- 숙소는 인기 1~3위 또는 공식 호텔업 등급(한국관광협회중앙회) 둘 중 하나로 검증했다. 정보 · 사진은 TourAPI.
-- 선정 근거 · 빠진 곳과 그 이유: geojero .outputs/food-stay-candidates-2026-09-19.md
--
-- 키는 TourAPI 국문 contentId 다(자연키) — serial 을 쓰지 않아 환경마다 같다(V36 사고의 교훈). API 의 placeId 도 이 값이다.
-- 이름 · 좌표는 TourAPI title · mapy/mapx 원문(pois 와 같은 방식). 소개 · 사진 · 영업시간 · 체크인 등은 저장하지 않고 런타임에 부른다.
--
-- category: 숙소만 — 호텔업 등급이 있으면 「N성 호텔」, 없으면 TourAPI 분류(콘도 · 호텔). 맛집은 NULL(대표 메뉴를 런타임에 쓴다).
-- booking_url: 여기어때 숙소 페이지(2026-09-19 사용자 — TourAPI 예약 주소는 7곳 중 한 곳뿐이고 그것도 자체 누리집이다).
--   지금은 일반 링크다. 제휴 링크로 바꾸면 화면에 경제적 이해관계 표시가 필요하다(공정위 추천 · 보증 심사지침).
-- eng_content_id: 국문 사진이 0장인 숙소의 영문 TourAPI contentId — 한화리조트 거제 벨버디어(3445089, 객실 사진 2장).
--   「영문 사진은 쓰지 않는다」(2026-09-10)의 예외 — 사용자 결정(기준문서 §6).
CREATE TABLE places (
  content_id     integer PRIMARY KEY,
  kind           text     NOT NULL CHECK (kind IN ('FOOD', 'STAY')),
  sort_order     smallint NOT NULL,
  name           text     NOT NULL,
  lat            numeric(10, 7) NOT NULL,
  lng            numeric(10, 7) NOT NULL,
  category       text,
  grade          smallint CHECK (grade BETWEEN 1 AND 5),
  booking_url    text,
  eng_content_id integer,
  UNIQUE (kind, sort_order),
  CHECK (kind = 'STAY' OR (category IS NULL AND grade IS NULL AND booking_url IS NULL))
);

INSERT INTO places (content_id, kind, sort_order, name, lat, lng, category, grade, booking_url, eng_content_id) VALUES
  (2783696, 'FOOD', 1, '대박난맛집', 34.7721525, 128.6380248, NULL, NULL, NULL, NULL),
  (2753311, 'FOOD', 2, '강성횟집', 34.8315834, 128.7148777, NULL, NULL, NULL, NULL),
  (2753331, 'FOOD', 3, '하면옥', 34.9043986, 128.6231060, NULL, NULL, NULL, NULL),
  (2753321, 'FOOD', 4, '한꼬막두꼬막', 34.8310888, 128.7024871, NULL, NULL, NULL, NULL),
  (2856903, 'FOOD', 5, '쌤김밥', 34.8296278, 128.7037509, NULL, NULL, NULL, NULL),
  (2916411, 'FOOD', 6, '점순이네밥집', 34.9432040, 128.7169369, NULL, NULL, NULL, NULL),
  (2778359, 'FOOD', 7, '어방가', 34.8334190, 128.7013177, NULL, NULL, NULL, NULL),
  (2867399, 'FOOD', 8, '웅아물회', 34.8305862, 128.7118474, NULL, NULL, NULL, NULL),
  (2858010, 'FOOD', 9, '거제멸치쌈밥', 34.8351966, 128.7003927, NULL, NULL, NULL, NULL),
  (578976, 'FOOD', 10, '백만석', 34.8752075, 128.6268259, NULL, NULL, NULL, NULL),
  (2858749, 'FOOD', 11, '초정명가횟집 물회', 34.8335888, 128.7012830, NULL, NULL, NULL, NULL),
  (2857046, 'FOOD', 12, '장수굴국밥', 34.8917430, 128.6944529, NULL, NULL, NULL, NULL),
  (2578495, 'STAY', 1, '소노캄 거제', 34.8433682, 128.7029354, '콘도', NULL, 'https://www.yeogi.com/domestic-accommodations/6605', NULL),
  (2660777, 'STAY', 2, '한화리조트 거제 벨버디어', 35.0078394, 128.7116127, '콘도', NULL, 'https://www.yeogi.com/domestic-accommodations/55495', 3445089),
  (3049260, 'STAY', 3, '라마다 스위츠 거제', 34.8402992, 128.6993831, '호텔', NULL, 'https://www.yeogi.com/domestic-accommodations/66828', NULL),
  (2578995, 'STAY', 4, '호텔리베라 거제', 34.8112109, 128.7040889, '3성 호텔', 3, 'https://www.yeogi.com/domestic-accommodations/7049', NULL),
  (143125, 'STAY', 5, '거제삼성호텔', 34.8961548, 128.6123202, '4성 호텔', 4, 'https://www.yeogi.com/domestic-accommodations/6757', NULL),
  (976736, 'STAY', 6, '호텔상상', 34.8475732, 128.7098527, '2성 호텔', 2, 'https://www.yeogi.com/domestic-accommodations/57408', NULL),
  (2578536, 'STAY', 7, '도야거제가족호텔', 34.8160003, 128.7038231, '2성 호텔', 2, 'https://www.yeogi.com/domestic-accommodations/6768', NULL);
