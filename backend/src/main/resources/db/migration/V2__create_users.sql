-- V2: User domain tables — AUTH-01

CREATE TABLE permissions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissions_name (name),
    INDEX idx_permissions_deleted_at (deleted_at)
);

CREATE TABLE roles (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT      NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_name (name),
    INDEX idx_roles_deleted_at (deleted_at)
);

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BIGINT       NULL,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    INDEX idx_users_email (email),
    INDEX idx_users_deleted_at (deleted_at)
);

CREATE TABLE user_roles (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT      NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_roles_user_role (user_id, role_id),
    INDEX idx_user_roles_user_id (user_id),
    INDEX idx_user_roles_deleted_at (deleted_at)
);

CREATE TABLE role_permissions (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    role_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BIGINT      NULL,
    updated_at    DATETIME(6) NOT NULL,
    updated_by    BIGINT      NULL,
    deleted_at    DATETIME(6) NULL,
    deleted_by    BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_role_permissions_role_perm (role_id, permission_id),
    INDEX idx_role_permissions_role_id (role_id),
    INDEX idx_role_permissions_deleted_at (deleted_at)
);

-- Seed: default roles
INSERT INTO roles (name, created_at, updated_at) VALUES
    ('USER',           NOW(6), NOW(6)),
    ('ADMIN',          NOW(6), NOW(6)),
    ('FACILITY_OWNER', NOW(6), NOW(6));
