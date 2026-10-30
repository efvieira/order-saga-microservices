CREATE TABLE payments (
    order_id       UUID PRIMARY KEY,
    customer_id    VARCHAR(60)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(12)    NOT NULL,
    transaction_id VARCHAR(60),
    reason         VARCHAR(200)
);
