-- V8: Message domain tables — MESSAGE-01-v2

CREATE TABLE rooms (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    type       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    INDEX idx_rooms_deleted_at (deleted_at),
    INDEX idx_rooms_type_deleted_at (type, deleted_at)
);

CREATE TABLE room_participants (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT      NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_room_participants_room_user (room_id, user_id, deleted_at),
    INDEX idx_room_participants_room_id_deleted_at (room_id, deleted_at),
    INDEX idx_room_participants_user_id_deleted_at (user_id, deleted_at)
);

CREATE TABLE messages (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    room_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    INDEX idx_messages_room_id_deleted_at (room_id, deleted_at),
    INDEX idx_messages_deleted_at (deleted_at)
);
