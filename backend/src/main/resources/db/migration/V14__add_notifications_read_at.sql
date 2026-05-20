ALTER TABLE notifications
    ADD COLUMN read_at DATETIME(6) NULL AFTER sent_at;

CREATE INDEX idx_notifications_user_id_read_at ON notifications (user_id, read_at);
