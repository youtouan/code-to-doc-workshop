CREATE TABLE stock_balance (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    on_hand INTEGER NOT NULL DEFAULT 0 CHECK (on_hand BETWEEN 0 AND 2000000000),
    reserved INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    last_movement_at TIMESTAMP,
    UNIQUE(warehouse_id, product_id),
    CHECK (reserved <= on_hand)
);
CREATE TABLE stock_receipt (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    request_key VARCHAR(120) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost >= 0),
    receipt_date DATE NOT NULL,
    reference VARCHAR(80) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX stock_receipt_date_idx ON stock_receipt(warehouse_id, receipt_date);
CREATE TABLE stock_movement (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    balance_id BIGINT NOT NULL REFERENCES stock_balance(id),
    movement_type VARCHAR(30) NOT NULL,
    quantity_change INTEGER NOT NULL,
    reserved_change INTEGER NOT NULL,
    on_hand_after INTEGER NOT NULL CHECK (on_hand_after >= 0),
    reserved_after INTEGER NOT NULL CHECK (reserved_after >= 0),
    document_type VARCHAR(40) NOT NULL,
    document_id BIGINT NOT NULL,
    document_number VARCHAR(50) NOT NULL,
    actor VARCHAR(50) NOT NULL,
    note VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    CHECK (quantity_change <> 0 OR reserved_change <> 0),
    CHECK (reserved_after <= on_hand_after)
);
CREATE INDEX stock_movement_balance_idx ON stock_movement(balance_id, id);
CREATE INDEX stock_movement_document_idx ON stock_movement(document_type, document_id);
CREATE TABLE stock_reservation (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    order_line_id BIGINT NOT NULL UNIQUE REFERENCES sales_order_line(id),
    balance_id BIGINT NOT NULL REFERENCES stock_balance(id),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX stock_reservation_balance_idx ON stock_reservation(balance_id) WHERE quantity > 0;
