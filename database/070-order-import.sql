-- Batch-local idempotency claims. The order and its claim commit in the same row transaction.
CREATE TABLE batch_order_import (
    id BIGINT PRIMARY KEY DEFAULT nextval('wholesale_seq'),
    version INTEGER NOT NULL DEFAULT 0,
    external_key VARCHAR(120) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    order_id BIGINT NOT NULL UNIQUE REFERENCES sales_order(id),
    created_by_id BIGINT NOT NULL REFERENCES app_user(id),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
