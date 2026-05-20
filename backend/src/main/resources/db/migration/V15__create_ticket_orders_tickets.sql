-- V14: Ticketing domain — ticket_orders, tickets tables
-- TICKETING-03

CREATE TABLE ticket_orders (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL,
    payment_id       BIGINT      NULL,
    locked_event_id  BIGINT      NOT NULL,
    locked_seat_ids  TEXT        NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    created_by       BIGINT      NULL,
    updated_at       DATETIME(6) NOT NULL,
    updated_by       BIGINT      NULL,
    deleted_at       DATETIME(6) NULL,
    deleted_by       BIGINT      NULL,
    PRIMARY KEY (id),
    INDEX idx_ticket_orders_user_id (user_id),
    INDEX idx_ticket_orders_status_deleted_at (status, deleted_at),
    INDEX idx_ticket_orders_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE tickets (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_order_id BIGINT      NOT NULL,
    seat_id         BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    code            VARCHAR(64) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    created_by      BIGINT      NULL,
    updated_at      DATETIME(6) NOT NULL,
    updated_by      BIGINT      NULL,
    deleted_at      DATETIME(6) NULL,
    deleted_by      BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_code (code),
    INDEX idx_tickets_ticket_order_id (ticket_order_id),
    INDEX idx_tickets_seat_id (seat_id),
    INDEX idx_tickets_status_deleted_at (status, deleted_at),
    INDEX idx_tickets_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

ALTER TABLE tickets
    ADD COLUMN active_seat_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ISSUED' THEN seat_id ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE KEY uk_tickets_active_seat (active_seat_id);
