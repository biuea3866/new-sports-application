-- V11: rooms 테이블에 last_message_at 컬럼 추가 — MESSAGE-03

ALTER TABLE rooms
    ADD COLUMN last_message_at DATETIME(6) NULL AFTER name;

ALTER TABLE rooms
    ADD INDEX idx_rooms_last_message_at (last_message_at);

ALTER TABLE messages
    ADD INDEX idx_messages_room_id_created_at (room_id, created_at);
