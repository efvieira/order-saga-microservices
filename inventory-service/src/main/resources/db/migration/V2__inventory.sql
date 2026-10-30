CREATE TABLE stock_items (
    sku       VARCHAR(40) PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    available INT          NOT NULL CHECK (available >= 0),
    version   BIGINT       NOT NULL
);

CREATE TABLE reservations (
    order_id UUID PRIMARY KEY,
    released BOOLEAN NOT NULL
);

CREATE TABLE reservation_lines (
    order_id UUID        NOT NULL REFERENCES reservations (order_id),
    sku      VARCHAR(40) NOT NULL,
    quantity INT         NOT NULL
);

INSERT INTO stock_items (sku, name, available, version) VALUES
('NOTEBOOK-14', 'Notebook 14 polegadas', 20, 0),
('MOUSE-SEM-FIO', 'Mouse sem fio', 200, 0),
('MONITOR-27', 'Monitor 27 polegadas', 5, 0),
('TECLADO-MEC', 'Teclado mecânico', 0, 0);
