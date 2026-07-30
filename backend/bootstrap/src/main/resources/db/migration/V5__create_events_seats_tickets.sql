-- V5: Ticketing domain — events, seats tables
-- TICKETING-01

CREATE TABLE events (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(200) NOT NULL,
    venue      VARCHAR(200) NOT NULL,
    starts_at  DATETIME(6)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    INDEX idx_events_status (status),
    INDEX idx_events_starts_at (starts_at),
    INDEX idx_events_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE seats (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    event_id   BIGINT         NOT NULL,
    section    VARCHAR(50)    NOT NULL,
    row_no     VARCHAR(10)    NOT NULL,
    seat_no    VARCHAR(10)    NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    created_by BIGINT         NULL,
    updated_at DATETIME(6)    NOT NULL,
    updated_by BIGINT         NULL,
    deleted_at DATETIME(6)    NULL,
    deleted_by BIGINT         NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX ux_seats_event_section_row_seat (event_id, section, row_no, seat_no),
    INDEX idx_seats_event_id (event_id),
    INDEX idx_seats_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
