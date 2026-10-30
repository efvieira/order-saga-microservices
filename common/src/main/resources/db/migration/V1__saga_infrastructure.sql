-- Tabelas de infraestrutura da saga, presentes no banco de cada serviço.
CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(80) NOT NULL,
    message_key  VARCHAR(64) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts     INT         NOT NULL
);

CREATE INDEX idx_outbox_pending ON outbox (published_at, created_at);

CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
