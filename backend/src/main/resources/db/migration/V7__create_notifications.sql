CREATE TABLE notifications
(
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    channel     VARCHAR(20)     NOT NULL,
    template_id VARCHAR(100)    NOT NULL,
    payload     TEXT            NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    sent_at     DATETIME(6)     NULL,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_id_status (user_id, status)
);
