ALTER TABLE notifications
    ADD COLUMN event_id VARCHAR(128) NULL AFTER read_at;

CREATE UNIQUE INDEX idx_notifications_event_id ON notifications (event_id);
