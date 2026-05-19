CREATE TABLE notifications
(
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    channel     VARCHAR(20)     NOT NULL,
    template_id VARCHAR(100)    NOT NULL,
    payload     TEXT            NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    sent_at     DATETIME(6)     NULL,
    version     BIGINT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL,
    created_by  BIGINT          NULL,
    updated_at  DATETIME(6)     NOT NULL,
    updated_by  BIGINT          NULL,
    deleted_at  DATETIME(6)     NULL,
    deleted_by  BIGINT          NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_id_status (user_id, status),
    INDEX idx_notifications_deleted_at (deleted_at)
);
