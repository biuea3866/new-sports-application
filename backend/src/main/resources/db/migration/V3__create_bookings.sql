CREATE TABLE slots (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    facility_id VARCHAR(255)    NOT NULL,
    date        DATETIME(6)     NOT NULL,
    time_range  VARCHAR(11)     NOT NULL,
    capacity    INT             NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE bookings (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    slot_id     BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    payment_id  BIGINT          NULL,
    created_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_bookings_user_id_status ON bookings (user_id, status);
CREATE INDEX idx_bookings_created_at ON bookings (created_at);
