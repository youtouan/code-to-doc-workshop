CREATE TABLE stock_transfer (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    source_warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    destination_warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    status VARCHAR(30) NOT NULL CHECK (status IN
      ('DRAFT','SUBMITTED','APPROVED','IN_TRANSIT','PART_RECEIVED','COMPLETED','RECONCILED','CANCELLED')),
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_by_id BIGINT NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    dispatched_by VARCHAR(50),
    dispatched_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancellation_reason VARCHAR(500) NOT NULL DEFAULT '',
    CHECK (source_warehouse_id <> destination_warehouse_id),
    CHECK (status <> 'CANCELLED' OR (dispatched_at IS NULL AND length(trim(cancellation_reason)) > 0)),
    CHECK (status NOT IN ('IN_TRANSIT','PART_RECEIVED','COMPLETED','RECONCILED') OR dispatched_at IS NOT NULL)
);
CREATE INDEX stock_transfer_source_idx ON stock_transfer(source_warehouse_id,status,id);
CREATE INDEX stock_transfer_destination_idx ON stock_transfer(destination_warehouse_id,status,id);

CREATE TABLE stock_transfer_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    transfer_id BIGINT NOT NULL REFERENCES stock_transfer(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    dispatched_quantity INTEGER NOT NULL DEFAULT 0 CHECK (dispatched_quantity >= 0),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    lost_quantity INTEGER NOT NULL DEFAULT 0 CHECK (lost_quantity >= 0),
    unit_cost NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    UNIQUE(transfer_id,product_id),
    CHECK (dispatched_quantity=0 OR dispatched_quantity=quantity),
    CHECK (received_quantity+lost_quantity<=dispatched_quantity)
);
CREATE INDEX stock_transfer_line_product_idx ON stock_transfer_line(product_id,transfer_id);

CREATE TABLE stock_transfer_receipt (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    transfer_id BIGINT NOT NULL REFERENCES stock_transfer(id),
    number VARCHAR(50) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('RECEIPT','LOSS')),
    request_key VARCHAR(120) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CHECK (kind<>'LOSS' OR length(trim(note))>0)
);
CREATE INDEX stock_transfer_receipt_transfer_idx ON stock_transfer_receipt(transfer_id,id);

CREATE TABLE stock_transfer_receipt_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    receipt_id BIGINT NOT NULL REFERENCES stock_transfer_receipt(id),
    transfer_line_id BIGINT NOT NULL REFERENCES stock_transfer_line(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost >= 0),
    UNIQUE(receipt_id,transfer_line_id)
);
CREATE INDEX stock_transfer_receipt_line_transfer_idx ON stock_transfer_receipt_line(transfer_line_id);

CREATE TABLE stock_adjustment (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity_change INTEGER NOT NULL CHECK (quantity_change<>0 AND quantity_change BETWEEN -1000000 AND 1000000),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason))>0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PROPOSED','APPROVED','REJECTED','CANCELLED')),
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost >= 0),
    proposed_by_id BIGINT NOT NULL,
    proposed_by VARCHAR(50) NOT NULL,
    proposed_at TIMESTAMP NOT NULL,
    decided_by VARCHAR(50),
    decided_at TIMESTAMP,
    decision_reason VARCHAR(500) NOT NULL DEFAULT '',
    CHECK (status='PROPOSED' OR (decided_at IS NOT NULL AND length(trim(decision_reason))>0)),
    CHECK (status<>'APPROVED' OR proposed_by<>decided_by)
);
CREATE INDEX stock_adjustment_status_idx ON stock_adjustment(warehouse_id,status,proposed_at);
CREATE INDEX stock_adjustment_product_idx ON stock_adjustment(product_id,status);

CREATE TABLE stock_count (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('COUNTING','REVIEWED','APPROVED','CANCELLED')),
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_by_id BIGINT NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    reviewed_by VARCHAR(50),
    reviewed_at TIMESTAMP,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    cancellation_reason VARCHAR(500) NOT NULL DEFAULT '',
    CHECK (status<>'APPROVED' OR approved_at IS NOT NULL),
    CHECK (status<>'CANCELLED' OR length(trim(cancellation_reason))>0)
);
CREATE INDEX stock_count_warehouse_idx ON stock_count(warehouse_id,status,id);

CREATE TABLE stock_count_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    stock_count_id BIGINT NOT NULL REFERENCES stock_count(id),
    balance_id BIGINT NOT NULL REFERENCES stock_balance(id),
    snapshot_on_hand INTEGER NOT NULL CHECK (snapshot_on_hand BETWEEN 0 AND 2000000000),
    snapshot_reserved INTEGER NOT NULL CHECK (snapshot_reserved BETWEEN 0 AND snapshot_on_hand),
    snapshot_version INTEGER NOT NULL CHECK (snapshot_version>=0),
    snapshot_movement_at TIMESTAMP,
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost>=0),
    counted_quantity INTEGER CHECK (counted_quantity BETWEEN 0 AND 2000000000),
    note VARCHAR(500) NOT NULL DEFAULT '',
    counted_by VARCHAR(50),
    counted_at TIMESTAMP,
    holding BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(stock_count_id,balance_id)
);
CREATE UNIQUE INDEX stock_count_one_holder_idx ON stock_count_line(balance_id) WHERE holding=TRUE;
