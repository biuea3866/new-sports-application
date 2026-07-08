CREATE TABLE push_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_tokens_token (token),
    INDEX idx_push_tokens_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_push_tokens_deleted_at (deleted_at)
);
