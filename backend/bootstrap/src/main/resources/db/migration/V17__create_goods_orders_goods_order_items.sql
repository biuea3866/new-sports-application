-- V17: goods domain — goods_orders, goods_order_items tables

CREATE TABLE goods_orders
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    payment_id   BIGINT         NULL,
    created_at   DATETIME(6)    NOT NULL,
    created_by   BIGINT         NULL,
    updated_at   DATETIME(6)    NOT NULL,
    updated_by   BIGINT         NULL,
    deleted_at   DATETIME(6)    NULL,
    deleted_by   BIGINT         NULL,
    PRIMARY KEY (id),
    INDEX idx_goods_orders_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_goods_orders_status_deleted_at (status, deleted_at),
    INDEX idx_goods_orders_deleted_at (deleted_at)
);

CREATE TABLE goods_order_items
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    created_by BIGINT         NULL,
    updated_at DATETIME(6)    NOT NULL,
    updated_by BIGINT         NULL,
    deleted_at DATETIME(6)    NULL,
    deleted_by BIGINT         NULL,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_goods_order_items_order_product_deleted (order_id, product_id, deleted_at),
    INDEX idx_goods_order_items_order_id (order_id),
    INDEX idx_goods_order_items_deleted_at (deleted_at)
);
