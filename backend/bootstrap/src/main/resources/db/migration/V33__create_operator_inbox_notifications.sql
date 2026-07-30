CREATE TABLE operator_inbox_notifications
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT        NOT NULL,
    type              VARCHAR(32)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    body              VARCHAR(1000) NOT NULL,
    link              VARCHAR(500)  NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'UNREAD',
    read_at           DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    created_by        BIGINT        NULL,
    updated_at        DATETIME(6)   NOT NULL,
    updated_by        BIGINT        NULL,
    deleted_at        DATETIME(6)   NULL,
    deleted_by        BIGINT        NULL,
    PRIMARY KEY (id),
    INDEX idx_operator_inbox_recipient_deleted_at (recipient_user_id, deleted_at),
    INDEX idx_operator_inbox_recipient_status_deleted_at (recipient_user_id, status, deleted_at),
    INDEX idx_operator_inbox_type_deleted_at (type, deleted_at),
    INDEX idx_operator_inbox_deleted_at (deleted_at)
);
