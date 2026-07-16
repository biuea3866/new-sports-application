-- ============================================================================
-- dev 데모용 테스트 시드 (MySQL). 재실행 가능(idempotent) — [SEED]/seed.local 범위만 정리 후 재삽입.
-- 실행: scripts/seed-dev.sh (Mongo 시설 시드까지 함께 실행). 단독 실행 시:
--   docker exec -i sports-dev-mysql-1 mysql --default-character-set=utf8mb4 -uroot -proot sports < scripts/seed-dev.sql
-- 주의: 반드시 --default-character-set=utf8mb4 로 넣어야 한글이 깨지지 않는다.
-- prod 에는 절대 실행하지 않는다.
-- ============================================================================
SET @now = NOW(6);

-- ---- 정리 (역순 의존) ----
DELETE FROM room_participants WHERE room_id IN
  (SELECT id FROM rooms WHERE context_type='COMMUNITY' AND context_id IN
    (SELECT id FROM communities WHERE name LIKE '[SEED]%'));
DELETE FROM rooms WHERE context_type='COMMUNITY' AND context_id IN
  (SELECT id FROM communities WHERE name LIKE '[SEED]%');
DELETE FROM stocks WHERE product_id IN (SELECT id FROM products WHERE name LIKE '[SEED]%');
DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE title LIKE '[SEED]%');
DELETE FROM events WHERE title LIKE '[SEED]%';
DELETE FROM posts WHERE title LIKE '[SEED]%';
DELETE FROM recruitments WHERE title LIKE '[SEED]%';
DELETE FROM community_members WHERE community_id IN (SELECT id FROM communities WHERE name LIKE '[SEED]%');
DELETE FROM communities WHERE name LIKE '[SEED]%';
DELETE FROM limited_drops WHERE product_id IN (SELECT id FROM products WHERE name LIKE '[SEED]%');
DELETE FROM products WHERE name LIKE '[SEED]%';
DELETE FROM users WHERE email LIKE '%@seed.local';

-- ---- 유저 12명 (소유자/작성자 참조용, 로그인 안 함) ----
INSERT INTO users (email, password_hash, status, created_at, updated_at)
SELECT CONCAT('user', n, '@seed.local'),
       COALESCE((SELECT password_hash FROM users WHERE status='ACTIVE' ORDER BY id LIMIT 1), 'x'),
       'ACTIVE', @now, @now
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
      UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12) t;

SET @u1 = (SELECT id FROM users WHERE email='user1@seed.local');
SET @u2 = (SELECT id FROM users WHERE email='user2@seed.local');
SET @u3 = (SELECT id FROM users WHERE email='user3@seed.local');
-- 데모 로그인 계정(있으면)을 멤버로 넣기 위한 참조. 없으면 u1 로 대체.
SET @me = COALESCE((SELECT id FROM users WHERE email='e2e@example.com'), @u1);

-- ---- 상품 24개 + 재고 100 + 이미지 URL ----
INSERT INTO products (name, category, price, description, image_url, status, owner_id, seller_type, created_at, updated_at)
SELECT CONCAT('[SEED] ', ELT(1+(n MOD 8),'프로 축구공','런닝화 에어','테니스 라켓','농구 유니폼','요가매트','골프 장갑','배드민턴 셔틀콕','사이클 헬멧'), ' ', n),
       ELT(1+(n MOD 3),'EQUIPMENT','APPAREL','FOOTWEAR'),
       9900 + (n*1500),
       '테스트 상품 설명입니다. 품질 좋은 스포츠 용품.',
       CONCAT('https://picsum.photos/seed/sports', n, '/400/400'),
       'ACTIVE', @u1, 'B2C', @now, @now
FROM (SELECT @r:=@r+1 n FROM information_schema.columns, (SELECT @r:=0) x LIMIT 24) t;

INSERT INTO stocks (product_id, quantity, version, created_at, updated_at)
SELECT id, 100, 0, @now, @now FROM products WHERE name LIKE '[SEED]%';

-- ---- 경기 8개 + 좌석 12개씩 ----
INSERT INTO events (title, venue, starts_at, status, owner_id, created_at, updated_at)
SELECT CONCAT('[SEED] ', ELT(1+(n MOD 4),'K리그','KBL 농구','KBO 야구','ATP 테니스'), ' ', n, '경기'),
       ELT(1+(n MOD 4),'서울월드컵경기장','잠실실내체육관','고척스카이돔','올림픽테니스장'),
       DATE_ADD(@now, INTERVAL (n+1) DAY), 'OPEN', @u2, @now, @now
FROM (SELECT @e:=@e+1 n FROM information_schema.columns, (SELECT @e:=0) x LIMIT 8) t;

INSERT INTO seats (event_id, section, row_no, seat_no, price, created_at, updated_at)
SELECT e.id, ELT(1+(s MOD 3),'A','B','C'), CAST(1+FLOOR(s/4) AS CHAR), CAST(1+(s MOD 4) AS CHAR),
       30000 + (s*5000), @now, @now
FROM events e
JOIN (SELECT @s:=@s+1 s FROM information_schema.columns, (SELECT @s:=0) x LIMIT 12) seq
WHERE e.title LIKE '[SEED]%';

-- ---- 커뮤니티 10개 + 멤버 + 채팅방 + 참여자 ----
INSERT INTO communities (name, description, visibility, sport_category, host_user_id, created_at, updated_at)
SELECT CONCAT('[SEED] ', ELT(1+(n MOD 6),'주말 축구 모임','아침 러닝 크루','테니스 동호회','수영 클럽','자전거 라이딩','요가 모임'), ' ', n),
       '함께 운동해요! 초보 환영합니다.',
       IF(n MOD 4=0,'PRIVATE','PUBLIC'),
       ELT(1+(n MOD 6),'SOCCER','RUNNING','TENNIS','SWIMMING','CYCLING','YOGA'),
       ELT(1+(n MOD 3), @u1, @u2, @u3), @now, @now
FROM (SELECT @c:=@c+1 n FROM information_schema.columns, (SELECT @c:=0) x LIMIT 10) t;

INSERT INTO community_members (community_id, user_id, role, status, joined_at, created_at, updated_at)
SELECT id, host_user_id, 'HOST', 'ACTIVE', @now, @now, @now FROM communities WHERE name LIKE '[SEED]%';
INSERT INTO community_members (community_id, user_id, role, status, joined_at, created_at, updated_at)
SELECT id, @me, 'MEMBER', 'ACTIVE', @now, @now, @now
FROM communities WHERE name LIKE '[SEED]%' AND visibility='PUBLIC'
  AND host_user_id <> @me;

-- 커뮤니티별 GROUP 채팅방 + 방장/데모계정 참여자
INSERT INTO rooms (type, name, context_type, context_id, host_user_id, created_at, updated_at)
SELECT 'GROUP', c.name, 'COMMUNITY', c.id, c.host_user_id, @now, @now
FROM communities c WHERE c.name LIKE '[SEED]%';

INSERT INTO room_participants (room_id, user_id, joined_at, participant_type, can_speak, created_at, updated_at)
SELECT r.id, r.host_user_id, @now, 'MEMBER', 1, @now, @now
FROM rooms r JOIN communities c ON r.context_type='COMMUNITY' AND r.context_id=c.id
WHERE c.name LIKE '[SEED]%';
INSERT INTO room_participants (room_id, user_id, joined_at, participant_type, can_speak, created_at, updated_at)
SELECT r.id, @me, @now, 'MEMBER', 1, @now, @now
FROM rooms r JOIN communities c ON r.context_type='COMMUNITY' AND r.context_id=c.id
WHERE c.name LIKE '[SEED]%' AND c.visibility='PUBLIC' AND c.host_user_id <> @me;

-- ---- 게시글 30개 (전역 노출) ----
INSERT INTO posts (user_id, title, content, sport_category, global_listed, type, created_at, updated_at)
SELECT ELT(1+(n MOD 3), @u1, @u2, @u3),
       CONCAT('[SEED] ', ELT(1+(n MOD 5),'오늘 경기 후기','장비 추천 부탁','같이 운동해요','풋살 구장 정보','부상 예방 팁'), ' ', n),
       '테스트 게시글 본문입니다. 여러 줄에 걸친 내용을 담고 있어요. 자유롭게 의견 나눠요.',
       ELT(1+(n MOD 12),'SOCCER','BASKETBALL','BASEBALL','TENNIS','BADMINTON','GOLF','RUNNING','CYCLING','SWIMMING','HIKING','YOGA','ETC'),
       1, ELT(1+(n MOD 4),'FREE','QUESTION','REVIEW','NOTICE'),
       DATE_SUB(@now, INTERVAL n MINUTE), @now
FROM (SELECT @p:=@p+1 n FROM information_schema.columns, (SELECT @p:=0) x LIMIT 30) t;

-- ---- 모집 10개 ----
INSERT INTO recruitments (title, description, capacity, fee_amount, activity_at, application_deadline, recruiter_user_id, status, created_at, updated_at)
SELECT CONCAT('[SEED] ', ELT(1+(n MOD 4),'주말 풋살 멤버','한강 러닝 크루','테니스 레슨','등산 번개'), ' ', n),
       '함께 하실 분 모집합니다. 장소·시간은 채팅으로 안내드려요.',
       6 + (n MOD 10), 5000 * (n MOD 5),
       DATE_ADD(@now, INTERVAL (n+2) DAY), DATE_ADD(@now, INTERVAL (n+1) DAY),
       ELT(1+(n MOD 3), @u1, @u2, @u3), 'OPEN', @now, @now
FROM (SELECT @rc:=@rc+1 n FROM information_schema.columns, (SELECT @rc:=0) x LIMIT 10) t;

-- ---- 검증 출력 ----
SELECT
  (SELECT COUNT(*) FROM users WHERE email LIKE '%@seed.local') AS users,
  (SELECT COUNT(*) FROM products WHERE name LIKE '[SEED]%') AS products,
  (SELECT COUNT(*) FROM events WHERE title LIKE '[SEED]%') AS events,
  (SELECT COUNT(*) FROM seats s JOIN events e ON s.event_id=e.id WHERE e.title LIKE '[SEED]%') AS seats,
  (SELECT COUNT(*) FROM communities WHERE name LIKE '[SEED]%') AS communities,
  (SELECT COUNT(*) FROM rooms r JOIN communities c ON r.context_id=c.id WHERE r.context_type='COMMUNITY' AND c.name LIKE '[SEED]%') AS rooms,
  (SELECT COUNT(*) FROM posts WHERE title LIKE '[SEED]%') AS posts,
  (SELECT COUNT(*) FROM recruitments WHERE title LIKE '[SEED]%') AS recruitments;
