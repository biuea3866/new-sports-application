-- V3: Post/Comment domain tables — POST-02

CREATE TABLE posts (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    content    VARCHAR(10000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    created_by BIGINT        NULL,
    updated_at DATETIME(6)   NOT NULL,
    updated_by BIGINT        NULL,
    deleted_at DATETIME(6)   NULL,
    deleted_by BIGINT        NULL,
    PRIMARY KEY (id),
    INDEX idx_posts_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_posts_deleted_at (deleted_at)
);

CREATE TABLE comments (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    INDEX idx_comments_post_id_deleted_at (post_id, deleted_at),
    INDEX idx_comments_deleted_at (deleted_at)
);
