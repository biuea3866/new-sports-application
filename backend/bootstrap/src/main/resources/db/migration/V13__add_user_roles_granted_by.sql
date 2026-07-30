ALTER TABLE user_roles
    ADD COLUMN granted_by BIGINT NULL AFTER role_id;
