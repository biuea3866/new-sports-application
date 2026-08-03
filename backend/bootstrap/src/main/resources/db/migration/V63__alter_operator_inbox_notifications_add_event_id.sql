-- 운영 인박스 알림에 이벤트 멱등 키(event_id)를 추가한다.
--
-- 배경: 운영 인박스 적재를 Kafka 도메인 이벤트 구독으로 시작한다. Kafka는 at-least-once라
-- 같은 이벤트를 두 번 받는 것이 정상 시나리오이며, 멱등 키가 없으면 파트너 인박스에 같은
-- 알림이 중복으로 쌓인다(안읽음 배지도 함께 부풀어 오른다).
--
-- nullable로 추가한다 — 기존 행에는 이벤트 출처가 없고, 운영자가 직접 만드는 알림 등
-- 이벤트에서 비롯되지 않은 적재 경로도 허용해야 한다. 백필은 하지 않는다(기존 0건).
-- MySQL의 UNIQUE 인덱스는 NULL을 중복으로 보지 않으므로, event_id가 NULL인 행은
-- 제약에 걸리지 않는다.
--
-- 롤백: ALTER TABLE operator_inbox_notifications
--         DROP INDEX uk_operator_inbox_recipient_event, DROP COLUMN event_id;

ALTER TABLE operator_inbox_notifications
    ADD COLUMN event_id VARCHAR(100) NULL COMMENT '이벤트 멱등 키 — 같은 이벤트 중복 수신 시 재적재를 막는다',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 수신자별로 같은 이벤트가 한 번만 적재되게 한다. 한 이벤트가 여러 수신자에게 갈 수 있으므로
-- event_id 단독이 아니라 (recipient_user_id, event_id) 복합으로 잡는다.
CREATE UNIQUE INDEX uk_operator_inbox_recipient_event
    ON operator_inbox_notifications (recipient_user_id, event_id)
    ALGORITHM = INPLACE, LOCK = NONE;
