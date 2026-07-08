-- =============================================================================
-- QA E2E 회귀 시드 — MySQL (database: sports)
-- =============================================================================
-- 목적: E2E 90 케이스 중 시드 부재로 test.skip() 되던 케이스를 실제 실행시킨다.
--
-- 적용 대상 케이스 (이 SQL 로 해제):
--   E2E-04-R01  GET /payments/me 결과의 createdAt ISO-8601  -> payments user_id=1 시드
--   E2E-05-R01  GET /events 의 startsAt ISO-8601            -> events 시드
--   E2E-08-R01  메시지 cursor 페이징                          -> rooms + room_participants + messages 시드
--
-- 이 SQL 로 해제되지 않는 케이스 (사유는 완료 보고 참조):
--   E2E-02-04 / E2E-02-05  -> facilities 는 MongoDB. seed-mongo.js 참조.
--   E2E-03-R02             -> spec 이 paymentMethod:"CARD" (유효하지 않은 enum) 전송 -> 500.
--   E2E-04-02              -> spec 이 method:"CARD" 전송 -> 500.
--   E2E-04-E04             -> 결제 게이트웨이 5xx stub 미존재.
--   E2E-05-R02             -> spec 이 method:"CARD" + 휘발성 Redis lock 의존.
--   E2E-06-R02             -> spec 이 items:[] 전송 -> BE 가 EmptyOrderException 무조건 throw.
--   E2E-07-R01             -> /portal 미인증 진입 -> 대시보드 숫자 미렌더.
--
-- 멱등: 모든 INSERT 가 명시 PK + ON DUPLICATE KEY UPDATE. 반복 주입해도 동일 상태.
--   (harness 규칙상 하드 삭제 금지 — upsert 만 사용)
-- 주의: users (id 1~6) / roles 는 기존 데이터를 그대로 사용한다 (재시드 안 함).
-- 모든 시각은 UTC. MySQL 컨테이너 time_zone = +00:00.
--
-- 주입 시 반드시 utf8mb4 클라이언트 charset 지정 (한글 mojibake 방지):
--   docker exec -i qa-mysql mysql --default-character-set=utf8mb4 -uroot sports < seed.sql
-- =============================================================================

SET @now = UTC_TIMESTAMP(6);

-- -----------------------------------------------------------------------------
-- 1. slots — facility_id 는 MongoDB facilities 의 _id 와 일치 (fac-001 ~ fac-003)
--    /facilities/{id}/slots 조회가 동작하도록 함. E2E-02-05 보조.
--    slot id=1 (capacity 5): E2E-03-E02 동시성 케이스용 — 회귀 누적으로 곧 full 이 되며
--      "동시 booking 시 한 쪽만 성공" 단언은 슬롯이 full 일수록 안정적이다.
--    slot id=7 (capacity 100000): E2E-03-R02 가 booking 을 생성해야 하는 케이스용.
--      booking 은 soft-delete 만 가능해 회귀 반복 시 PENDING 이 누적되므로 capacity 를
--      크게 둬 SlotFull(409) 로 skip 되지 않게 한다.
-- -----------------------------------------------------------------------------
INSERT INTO slots (id, facility_id, date, time_range, capacity, owner_id, created_at, updated_at) VALUES
  (1, 'fac-001', '2026-06-01 09:00:00.000000', '09:00-11:00', 5, 3, @now, @now),
  (2, 'fac-001', '2026-06-01 11:00:00.000000', '11:00-13:00', 5, 3, @now, @now),
  (3, 'fac-001', '2026-06-01 14:00:00.000000', '14:00-16:00', 5, 3, @now, @now),
  (4, 'fac-002', '2026-06-02 10:00:00.000000', '10:00-12:00', 8, 3, @now, @now),
  (5, 'fac-002', '2026-06-02 13:00:00.000000', '13:00-15:00', 8, 3, @now, @now),
  (6, 'fac-003', '2026-06-03 18:00:00.000000', '18:00-20:00', 10, 3, @now, @now),
  (7, 'fac-001', '2026-06-04 09:00:00.000000', '09:00-11:00', 100000, 3, @now, @now)
ON DUPLICATE KEY UPDATE
  facility_id = VALUES(facility_id), date = VALUES(date), time_range = VALUES(time_range),
  capacity = VALUES(capacity), owner_id = VALUES(owner_id), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 2. events + seats — E2E-05-R01 (GET /events startsAt ISO-8601), E2E-05-02/03 보조
--    status: SCHEDULED / OPEN (EventStatus enum). starts_at 은 미래 시각.
-- -----------------------------------------------------------------------------
INSERT INTO events (id, title, venue, starts_at, status, owner_id, created_at, updated_at) VALUES
  (1, 'K리그 클래식 서울 vs 전북', '서울월드컵경기장', '2026-07-10 19:00:00.000000', 'OPEN',       4, @now, @now),
  (2, 'KBL 챔피언결정전 1차전',     '잠실실내체육관',   '2026-07-15 19:30:00.000000', 'OPEN',       4, @now, @now),
  (3, 'V리그 올스타전',            '장충체육관',       '2026-08-01 17:00:00.000000', 'SCHEDULED',  4, @now, @now)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), venue = VALUES(venue), starts_at = VALUES(starts_at),
  status = VALUES(status), owner_id = VALUES(owner_id), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

INSERT INTO seats (id, event_id, section, row_no, seat_no, price, created_at, updated_at) VALUES
  (1,  1, 'A', '1', '1',  35000.00, @now, @now),
  (2,  1, 'A', '1', '2',  35000.00, @now, @now),
  (3,  1, 'A', '1', '3',  35000.00, @now, @now),
  (4,  1, 'A', '1', '4',  35000.00, @now, @now),
  (5,  1, 'B', '1', '1',  28000.00, @now, @now),
  (6,  1, 'B', '1', '2',  28000.00, @now, @now),
  (7,  2, 'A', '1', '1',  45000.00, @now, @now),
  (8,  2, 'A', '1', '2',  45000.00, @now, @now),
  (9,  2, 'A', '1', '3',  45000.00, @now, @now),
  (10, 2, 'B', '1', '1',  30000.00, @now, @now),
  (11, 3, 'A', '1', '1',  20000.00, @now, @now),
  (12, 3, 'A', '1', '2',  20000.00, @now, @now)
ON DUPLICATE KEY UPDATE
  event_id = VALUES(event_id), section = VALUES(section), row_no = VALUES(row_no),
  seat_no = VALUES(seat_no), price = VALUES(price), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 3. products + stocks — E2E-06-01~04 보조 (카테고리/키워드/가격 필터)
--    category: EQUIPMENT / APPAREL / FOOTWEAR / ACCESSORY (ProductCategory enum)
--    status: ACTIVE / INACTIVE (ProductStatus enum)
-- -----------------------------------------------------------------------------
INSERT INTO products (id, name, category, price, description, image_url, status, owner_id, created_at, updated_at) VALUES
  (1, '국가대표 유니폼 2026', 'APPAREL',   42000.00, '공식 국가대표 유니폼 레플리카', 'https://cdn.test.local/p1.jpg', 'ACTIVE', 5, @now, @now),
  (2, '풋살화 프로 X',        'FOOTWEAR',  68000.00, '실내 풋살 전용 슈즈',          'https://cdn.test.local/p2.jpg', 'ACTIVE', 5, @now, @now),
  (3, '축구공 5호 공인구',     'EQUIPMENT', 35000.00, 'FIFA 공인 5호 축구공',         'https://cdn.test.local/p3.jpg', 'ACTIVE', 5, @now, @now),
  (4, '농구공 7호',          'EQUIPMENT', 29000.00, '실내외 겸용 7호 농구공',        'https://cdn.test.local/p4.jpg', 'ACTIVE', 5, @now, @now),
  (5, '스포츠 손목밴드',      'ACCESSORY', 12000.00, '땀 흡수 손목밴드 2개입',        'https://cdn.test.local/p5.jpg', 'ACTIVE', 5, @now, @now),
  (6, '클럽 유니폼 어웨이',   'APPAREL',   38000.00, '클럽 어웨이 유니폼',           'https://cdn.test.local/p6.jpg', 'ACTIVE', 5, @now, @now)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), category = VALUES(category), price = VALUES(price),
  description = VALUES(description), image_url = VALUES(image_url), status = VALUES(status),
  owner_id = VALUES(owner_id), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

INSERT INTO stocks (product_id, quantity, version, created_at, updated_at) VALUES
  (1, 100, 0, @now, @now),
  (2,  50, 0, @now, @now),
  (3, 200, 0, @now, @now),
  (4, 150, 0, @now, @now),
  (5, 300, 0, @now, @now),
  (6,  80, 0, @now, @now)
ON DUPLICATE KEY UPDATE
  quantity = VALUES(quantity), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 4. payments — E2E-04-R01 (GET /payments/me createdAt ISO-8601)
--    POST /payments 는 userId 를 1L 로 하드코딩 -> /payments/me X-User-Id:1 이 조회.
--    method: CREDIT_CARD (PaymentMethod enum), status: COMPLETED (PaymentStatus enum).
--    createdAt 을 서로 다르게 두어 정렬(DESC) 검증도 의미를 갖게 함.
-- -----------------------------------------------------------------------------
INSERT INTO payments
  (id, user_id, idempotency_key, order_type, order_id, method, amount, currency, status, paid_at, created_at, updated_at) VALUES
  (1, 1, 'qa-seed-pay-0001', 'BOOKING',   1, 'CREDIT_CARD', 50000.00, 'KRW', 'COMPLETED', '2026-05-20 03:00:00.000000', '2026-05-20 03:00:00.000000', '2026-05-20 03:00:00.000000'),
  (2, 1, 'qa-seed-pay-0002', 'TICKETING', 1, 'CREDIT_CARD', 70000.00, 'KRW', 'COMPLETED', '2026-05-21 06:30:00.000000', '2026-05-21 06:30:00.000000', '2026-05-21 06:30:00.000000'),
  (3, 1, 'qa-seed-pay-0003', 'GOODS',     1, 'MOBILE_PAY',  42000.00, 'KRW', 'COMPLETED', '2026-05-22 01:15:00.000000', '2026-05-22 01:15:00.000000', '2026-05-22 01:15:00.000000'),
  (4, 2, 'qa-seed-pay-0004', 'BOOKING',   2, 'CREDIT_CARD', 30000.00, 'KRW', 'COMPLETED', '2026-05-22 02:00:00.000000', '2026-05-22 02:00:00.000000', '2026-05-22 02:00:00.000000')
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id), order_type = VALUES(order_type), order_id = VALUES(order_id),
  method = VALUES(method), amount = VALUES(amount), currency = VALUES(currency),
  status = VALUES(status), paid_at = VALUES(paid_at), created_at = VALUES(created_at),
  updated_at = VALUES(updated_at), deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 5. rooms + room_participants + messages — E2E-08-R01 (cursor 페이징)
--    room id=1 에 user 1, user 2 를 참여자로 둔다 (E2E-08-R01/06 은 X-User-Id:1).
--    messages 35건 — PAGE_SIZE(30) 초과 -> nextCursor 가 실제로 non-null 이 된다.
--    created_at 은 1분 간격 증가 — 시간 정렬 검증에 사용.
-- -----------------------------------------------------------------------------
INSERT INTO rooms (id, type, name, last_message_at, created_at, updated_at) VALUES
  (1, 'GROUP',  '강남 풋살 동호회', @now, @now, @now),
  (2, 'DIRECT', NULL,             @now, @now, @now)
ON DUPLICATE KEY UPDATE
  type = VALUES(type), name = VALUES(name), last_message_at = VALUES(last_message_at),
  updated_at = VALUES(updated_at), deleted_at = NULL, deleted_by = NULL;

INSERT INTO room_participants (id, room_id, user_id, joined_at, created_at, updated_at) VALUES
  (1, 1, 1, @now, @now, @now),
  (2, 1, 2, @now, @now, @now),
  (3, 1, 3, @now, @now, @now),
  (4, 2, 1, @now, @now, @now),
  (5, 2, 2, @now, @now, @now)
ON DUPLICATE KEY UPDATE
  room_id = VALUES(room_id), user_id = VALUES(user_id), joined_at = VALUES(joined_at),
  updated_at = VALUES(updated_at), deleted_at = NULL, deleted_by = NULL;

-- messages: room 1 에 35건. created_at = 2026-05-22 00:00:00 + N분.
INSERT INTO messages (id, room_id, user_id, content, created_at, updated_at) VALUES
  (1,  1, 1, 'qa-seed 메시지 01', '2026-05-22 00:01:00.000000', '2026-05-22 00:01:00.000000'),
  (2,  1, 2, 'qa-seed 메시지 02', '2026-05-22 00:02:00.000000', '2026-05-22 00:02:00.000000'),
  (3,  1, 3, 'qa-seed 메시지 03', '2026-05-22 00:03:00.000000', '2026-05-22 00:03:00.000000'),
  (4,  1, 1, 'qa-seed 메시지 04', '2026-05-22 00:04:00.000000', '2026-05-22 00:04:00.000000'),
  (5,  1, 2, 'qa-seed 메시지 05', '2026-05-22 00:05:00.000000', '2026-05-22 00:05:00.000000'),
  (6,  1, 3, 'qa-seed 메시지 06', '2026-05-22 00:06:00.000000', '2026-05-22 00:06:00.000000'),
  (7,  1, 1, 'qa-seed 메시지 07', '2026-05-22 00:07:00.000000', '2026-05-22 00:07:00.000000'),
  (8,  1, 2, 'qa-seed 메시지 08', '2026-05-22 00:08:00.000000', '2026-05-22 00:08:00.000000'),
  (9,  1, 3, 'qa-seed 메시지 09', '2026-05-22 00:09:00.000000', '2026-05-22 00:09:00.000000'),
  (10, 1, 1, 'qa-seed 메시지 10', '2026-05-22 00:10:00.000000', '2026-05-22 00:10:00.000000'),
  (11, 1, 2, 'qa-seed 메시지 11', '2026-05-22 00:11:00.000000', '2026-05-22 00:11:00.000000'),
  (12, 1, 3, 'qa-seed 메시지 12', '2026-05-22 00:12:00.000000', '2026-05-22 00:12:00.000000'),
  (13, 1, 1, 'qa-seed 메시지 13', '2026-05-22 00:13:00.000000', '2026-05-22 00:13:00.000000'),
  (14, 1, 2, 'qa-seed 메시지 14', '2026-05-22 00:14:00.000000', '2026-05-22 00:14:00.000000'),
  (15, 1, 3, 'qa-seed 메시지 15', '2026-05-22 00:15:00.000000', '2026-05-22 00:15:00.000000'),
  (16, 1, 1, 'qa-seed 메시지 16', '2026-05-22 00:16:00.000000', '2026-05-22 00:16:00.000000'),
  (17, 1, 2, 'qa-seed 메시지 17', '2026-05-22 00:17:00.000000', '2026-05-22 00:17:00.000000'),
  (18, 1, 3, 'qa-seed 메시지 18', '2026-05-22 00:18:00.000000', '2026-05-22 00:18:00.000000'),
  (19, 1, 1, 'qa-seed 메시지 19', '2026-05-22 00:19:00.000000', '2026-05-22 00:19:00.000000'),
  (20, 1, 2, 'qa-seed 메시지 20', '2026-05-22 00:20:00.000000', '2026-05-22 00:20:00.000000'),
  (21, 1, 3, 'qa-seed 메시지 21', '2026-05-22 00:21:00.000000', '2026-05-22 00:21:00.000000'),
  (22, 1, 1, 'qa-seed 메시지 22', '2026-05-22 00:22:00.000000', '2026-05-22 00:22:00.000000'),
  (23, 1, 2, 'qa-seed 메시지 23', '2026-05-22 00:23:00.000000', '2026-05-22 00:23:00.000000'),
  (24, 1, 3, 'qa-seed 메시지 24', '2026-05-22 00:24:00.000000', '2026-05-22 00:24:00.000000'),
  (25, 1, 1, 'qa-seed 메시지 25', '2026-05-22 00:25:00.000000', '2026-05-22 00:25:00.000000'),
  (26, 1, 2, 'qa-seed 메시지 26', '2026-05-22 00:26:00.000000', '2026-05-22 00:26:00.000000'),
  (27, 1, 3, 'qa-seed 메시지 27', '2026-05-22 00:27:00.000000', '2026-05-22 00:27:00.000000'),
  (28, 1, 1, 'qa-seed 메시지 28', '2026-05-22 00:28:00.000000', '2026-05-22 00:28:00.000000'),
  (29, 1, 2, 'qa-seed 메시지 29', '2026-05-22 00:29:00.000000', '2026-05-22 00:29:00.000000'),
  (30, 1, 3, 'qa-seed 메시지 30', '2026-05-22 00:30:00.000000', '2026-05-22 00:30:00.000000'),
  (31, 1, 1, 'qa-seed 메시지 31', '2026-05-22 00:31:00.000000', '2026-05-22 00:31:00.000000'),
  (32, 1, 2, 'qa-seed 메시지 32', '2026-05-22 00:32:00.000000', '2026-05-22 00:32:00.000000'),
  (33, 1, 3, 'qa-seed 메시지 33', '2026-05-22 00:33:00.000000', '2026-05-22 00:33:00.000000'),
  (34, 1, 1, 'qa-seed 메시지 34', '2026-05-22 00:34:00.000000', '2026-05-22 00:34:00.000000'),
  (35, 1, 2, 'qa-seed 메시지 35', '2026-05-22 00:35:00.000000', '2026-05-22 00:35:00.000000')
ON DUPLICATE KEY UPDATE
  room_id = VALUES(room_id), user_id = VALUES(user_id), content = VALUES(content),
  created_at = VALUES(created_at), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 6. notifications — E2E-08-01~03 보조 (목록/미읽음 카운트가 0 이 아니도록)
--    user 1 에 3건 (1건 읽음, 2건 미읽음).
--    payload 컬럼은 @Type(JsonStringType) NotificationPayload — {"data":{...}} 형태 필수.
--    channel: IN_APP/PUSH/EMAIL/SMS, status: QUEUED/SENT/FAILED (도메인 enum).
-- -----------------------------------------------------------------------------
INSERT INTO notifications
  (id, user_id, channel, template_id, payload, status, sent_at, read_at, version, created_at, updated_at) VALUES
  (1, 1, 'EMAIL', 'BOOKING_CONFIRMED', '{"data":{"bookingId":1}}', 'SENT', @now, @now, 0, @now, @now),
  (2, 1, 'PUSH',  'PAYMENT_COMPLETED', '{"data":{"paymentId":1}}', 'SENT', @now, NULL, 0, @now, @now),
  (3, 1, 'EMAIL', 'EVENT_REMINDER',    '{"data":{"eventId":1}}',   'SENT', @now, NULL, 0, @now, @now)
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id), channel = VALUES(channel), template_id = VALUES(template_id),
  payload = VALUES(payload), status = VALUES(status), sent_at = VALUES(sent_at),
  read_at = VALUES(read_at), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- -----------------------------------------------------------------------------
-- 7. portal 대시보드 fixture — E2E-07-R01 (로그인 후 /portal 진입 → 대시보드 숫자 렌더)
--    user id=100 을 고정 fixture 계정으로 둔다 (email/password 고정 → spec 이 loginUser 로 사용).
--    password_hash 는 BCrypt("Passw0rd!") — helpers.ts registerUser 기본 비밀번호와 동일.
--    user_roles: FACILITY_OWNER(3) + EVENT_HOST(4) + GOODS_SELLER(5) 3개 역할 부여
--      → 대시보드 3개 섹션이 모두 렌더된다.
--    events/products 를 owner_id=100 으로 시드 → 섹션 숫자가 0 이 아니게 한다.
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, status, created_at, updated_at) VALUES
  (100, 'qa-portal-fixture@test.local', '$2a$10$KJEyU5O4bsGEiyYXn1A4zuqR4coydOOv/JZtPYNqvS9JJCL8ftgDq', 'ACTIVE', @now, @now)
ON DUPLICATE KEY UPDATE
  email = VALUES(email), password_hash = VALUES(password_hash), status = VALUES(status),
  updated_at = VALUES(updated_at), deleted_at = NULL, deleted_by = NULL;

-- user_roles: id 는 auto_increment — 자연 unique 키 (user_id, role_id, deleted_at) 로 멱등 처리.
INSERT INTO user_roles (user_id, role_id, granted_by, created_at, updated_at) VALUES
  (100, 3, NULL, @now, @now),
  (100, 4, NULL, @now, @now),
  (100, 5, NULL, @now, @now)
ON DUPLICATE KEY UPDATE
  granted_by = VALUES(granted_by), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- fixture user 소유 events — 대시보드 events 섹션: 전체 4, 예정 1, 오픈 3
INSERT INTO events (id, title, venue, starts_at, status, owner_id, created_at, updated_at) VALUES
  (100, 'QA 포털 fixture 경기 A', '서울월드컵경기장', '2026-09-01 19:00:00.000000', 'OPEN',      100, @now, @now),
  (101, 'QA 포털 fixture 경기 B', '잠실실내체육관',   '2026-09-05 19:00:00.000000', 'OPEN',      100, @now, @now),
  (102, 'QA 포털 fixture 경기 C', '장충체육관',       '2026-09-10 17:00:00.000000', 'OPEN',      100, @now, @now),
  (103, 'QA 포털 fixture 경기 D', '고척스카이돔',     '2026-10-01 18:00:00.000000', 'SCHEDULED', 100, @now, @now)
ON DUPLICATE KEY UPDATE
  title = VALUES(title), venue = VALUES(venue), starts_at = VALUES(starts_at),
  status = VALUES(status), owner_id = VALUES(owner_id), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- fixture user 소유 products + stocks — 대시보드 products 섹션: 전체 3, 판매중 3, 품절 1
INSERT INTO products (id, name, category, price, description, image_url, status, owner_id, created_at, updated_at) VALUES
  (100, 'QA 포털 fixture 상품 A', 'APPAREL',   30000.00, 'fixture 상품 A', 'https://cdn.test.local/f1.jpg', 'ACTIVE', 100, @now, @now),
  (101, 'QA 포털 fixture 상품 B', 'EQUIPMENT', 25000.00, 'fixture 상품 B', 'https://cdn.test.local/f2.jpg', 'ACTIVE', 100, @now, @now),
  (102, 'QA 포털 fixture 상품 C', 'ACCESSORY', 15000.00, 'fixture 상품 C', 'https://cdn.test.local/f3.jpg', 'ACTIVE', 100, @now, @now)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), category = VALUES(category), price = VALUES(price),
  description = VALUES(description), image_url = VALUES(image_url), status = VALUES(status),
  owner_id = VALUES(owner_id), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

INSERT INTO stocks (product_id, quantity, version, created_at, updated_at) VALUES
  (100, 50, 0, @now, @now),
  (101,  0, 0, @now, @now),
  (102, 30, 0, @now, @now)
ON DUPLICATE KEY UPDATE
  quantity = VALUES(quantity), updated_at = VALUES(updated_at),
  deleted_at = NULL, deleted_by = NULL;

-- =============================================================================
-- 끝. 주입 후 검증: SELECT COUNT(*) FROM <table>;
-- =============================================================================
