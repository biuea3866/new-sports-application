-- V6: goods domain — products, stocks tables
CREATE TABLE products
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)   NOT NULL,
    category    VARCHAR(50)    NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    description TEXT,
    image_url   VARCHAR(2048),
    status      VARCHAR(20)    NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    created_by  BIGINT         NULL,
    updated_at  DATETIME(6)    NOT NULL,
    updated_by  BIGINT         NULL,
    deleted_at  DATETIME(6)    NULL,
    deleted_by  BIGINT         NULL,
    PRIMARY KEY (id),
    INDEX idx_products_category_status_price (category, status, price),
    INDEX idx_products_deleted_at (deleted_at)
);

CREATE TABLE stocks
(
    product_id BIGINT  NOT NULL,
    quantity   INT     NOT NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT      NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT      NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT      NULL,
    PRIMARY KEY (product_id),
    INDEX idx_stocks_deleted_at (deleted_at)
);
