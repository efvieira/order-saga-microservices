CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_id    VARCHAR(60)    NOT NULL,
    zip_code       VARCHAR(8)     NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(24)    NOT NULL,
    failure_reason VARCHAR(200),
    transaction_id VARCHAR(60),
    tracking_code  VARCHAR(60),
    version        BIGINT         NOT NULL
);

CREATE TABLE order_lines (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    position   INT            NOT NULL,
    sku        VARCHAR(40)    NOT NULL,
    quantity   INT            NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, position)
);

CREATE TABLE order_history (
    order_id   UUID        NOT NULL REFERENCES orders (id),
    position   INT         NOT NULL,
    status     VARCHAR(24) NOT NULL,
    detail     VARCHAR(200),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (order_id, position)
);
