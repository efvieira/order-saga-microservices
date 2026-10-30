CREATE TABLE shipments (
    order_id      UUID PRIMARY KEY,
    zip_code      VARCHAR(8)  NOT NULL,
    tracking_code VARCHAR(20) NOT NULL,
    scheduled_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
