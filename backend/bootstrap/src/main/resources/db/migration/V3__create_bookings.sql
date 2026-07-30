CREATE TABLE slots (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    facility_id VARCHAR(255)    NOT NULL,
    date        DATETIME(6)     NOT NULL,
    time_range  VARCHAR(11)     NOT NULL,
    capacity    INT             NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    created_by  BIGINT          NULL,
    updated_at  DATETIME(6)     NOT NULL,
    updated_by  BIGINT          NULL,
    deleted_at  DATETIME(6)     NULL,
    deleted_by  BIGINT          NULL,
    PRIMARY KEY (id)
);

CREATE TABLE bookings (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    slot_id     BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    payment_id  BIGINT          NULL,
    created_at  DATETIME(6)     NOT NULL,
    created_by  BIGINT          NULL,
    updated_at  DATETIME(6)     NOT NULL,
    updated_by  BIGINT          NULL,
    deleted_at  DATETIME(6)     NULL,
    deleted_by  BIGINT          NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_bookings_user_id_status ON bookings (user_id, status);
CREATE INDEX idx_bookings_created_at ON bookings (created_at);
CREATE INDEX idx_bookings_deleted_at ON bookings (deleted_at);
CREATE INDEX idx_slots_deleted_at ON slots (deleted_at);
