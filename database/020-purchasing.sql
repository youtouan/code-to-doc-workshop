CREATE TABLE supplier (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    code VARCHAR(30) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,29}$'),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    on_hold BOOLEAN NOT NULL DEFAULT FALSE,
    closing_day INTEGER NOT NULL DEFAULT 31 CHECK (closing_day IN (10,20,31)),
    payment_term_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_term_days BETWEEN 0 AND 120),
    default_lead_time_days INTEGER NOT NULL DEFAULT 3 CHECK (default_lead_time_days BETWEEN 0 AND 365),
    minimum_order_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (minimum_order_amount BETWEEN 0 AND 999999999999.99),
    tax_rounding VARCHAR(10) NOT NULL DEFAULT 'DOWN' CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    postal_code VARCHAR(12) NOT NULL DEFAULT '',
    address VARCHAR(250) NOT NULL DEFAULT '',
    telephone VARCHAR(30) NOT NULL DEFAULT '',
    ordering_instructions VARCHAR(1000) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT ''
);
CREATE INDEX ix_supplier_active_code ON supplier(active,on_hold,code);

CREATE TABLE supplier_product (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    supplier_product_code VARCHAR(60) NOT NULL DEFAULT '',
    valid_from DATE NOT NULL,
    valid_to DATE,
    minimum_quantity INTEGER NOT NULL DEFAULT 1 CHECK (minimum_quantity BETWEEN 1 AND 1000000),
    order_pack_size INTEGER NOT NULL DEFAULT 1 CHECK (order_pack_size BETWEEN 1 AND 1000000),
    lead_time_days INTEGER NOT NULL DEFAULT 3 CHECK (lead_time_days BETWEEN 0 AND 365),
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost BETWEEN 0 AND 999999999999.99),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT ck_supplier_product_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_supplier_product_pack CHECK (minimum_quantity % order_pack_size = 0)
);
CREATE UNIQUE INDEX uq_supplier_product_active_tier ON supplier_product
    (supplier_id,product_id,minimum_quantity,valid_from) WHERE active;
CREATE INDEX ix_supplier_product_quote ON supplier_product(supplier_id,product_id,active,valid_from,valid_to,minimum_quantity);
CREATE INDEX ix_supplier_product_reorder ON supplier_product(product_id,active,preferred);

CREATE TABLE purchase_order (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    order_date DATE NOT NULL,
    expected_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED','PART_RECEIVED','RECEIVED','CLOSED')),
    supplier_name VARCHAR(120) NOT NULL,
    closing_day INTEGER NOT NULL CHECK (closing_day IN (10,20,31)),
    payment_term_days INTEGER NOT NULL CHECK (payment_term_days BETWEEN 0 AND 120),
    tax_rounding VARCHAR(10) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    ordering_instructions VARCHAR(1000) NOT NULL DEFAULT '',
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (total_amount BETWEEN 0 AND 999999999999.99),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    submitted_by VARCHAR(50),
    submitted_at TIMESTAMP,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    last_changed_by VARCHAR(50) NOT NULL,
    last_changed_at TIMESTAMP NOT NULL,
    decision_reason VARCHAR(500) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT ck_purchase_order_dates CHECK (expected_date >= order_date),
    CONSTRAINT ck_purchase_order_approval_pair CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CONSTRAINT ck_purchase_order_submission_pair CHECK ((submitted_by IS NULL) = (submitted_at IS NULL)),
    CONSTRAINT ck_purchase_order_separation CHECK (approved_by IS NULL OR
        (approved_by <> created_by AND approved_by <> submitted_by)),
    CONSTRAINT ck_purchase_order_approved_state CHECK (status NOT IN ('APPROVED','PART_RECEIVED','RECEIVED','CLOSED')
        OR (approved_by IS NOT NULL AND submitted_by IS NOT NULL))
);
CREATE INDEX ix_purchase_order_search ON purchase_order(status,order_date,id);
CREATE INDEX ix_purchase_order_supplier ON purchase_order(supplier_id,status);
CREATE INDEX ix_purchase_order_commitment ON purchase_order(warehouse_id,status);

CREATE TABLE purchase_order_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    order_id BIGINT NOT NULL REFERENCES purchase_order(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    product_id BIGINT NOT NULL REFERENCES product(id),
    product_code VARCHAR(30) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    supplier_product_code VARCHAR(60) NOT NULL DEFAULT '',
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    rejected_quantity INTEGER NOT NULL DEFAULT 0 CHECK (rejected_quantity >= 0),
    cancelled_quantity INTEGER NOT NULL DEFAULT 0 CHECK (cancelled_quantity >= 0),
    order_pack_size INTEGER NOT NULL CHECK (order_pack_size BETWEEN 1 AND 1000000),
    lead_time_days INTEGER NOT NULL CHECK (lead_time_days BETWEEN 0 AND 365),
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost BETWEEN 0 AND 999999999999.99),
    line_amount NUMERIC(18,2) NOT NULL CHECK (line_amount BETWEEN 0 AND 999999999999.99),
    expected_date DATE NOT NULL,
    notes VARCHAR(250) NOT NULL DEFAULT '',
    CONSTRAINT uq_purchase_order_line UNIQUE(order_id,line_number),
    CONSTRAINT uq_purchase_order_product UNIQUE(order_id,product_id),
    CONSTRAINT ck_purchase_order_fulfillment CHECK (received_quantity + cancelled_quantity <= quantity),
    CONSTRAINT ck_purchase_order_pack CHECK (quantity % order_pack_size = 0),
    CONSTRAINT ck_purchase_order_line_amount CHECK (line_amount = unit_cost * quantity)
);
CREATE INDEX ix_purchase_order_line_product ON purchase_order_line(product_id,order_id);
CREATE INDEX ix_purchase_order_line_due ON purchase_order_line(expected_date,order_id);

CREATE TABLE purchase_receipt (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    order_id BIGINT NOT NULL REFERENCES purchase_order(id),
    receipt_date DATE NOT NULL,
    supplier_delivery_number VARCHAR(60) NOT NULL DEFAULT '',
    received_by VARCHAR(50) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    accepted_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (accepted_amount BETWEEN 0 AND 999999999999.99),
    notes VARCHAR(1000) NOT NULL DEFAULT ''
);
CREATE INDEX ix_purchase_receipt_order ON purchase_receipt(order_id,receipt_date,id);

CREATE TABLE purchase_receipt_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    receipt_id BIGINT NOT NULL REFERENCES purchase_receipt(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    order_line_id BIGINT NOT NULL REFERENCES purchase_order_line(id),
    accepted_quantity INTEGER NOT NULL CHECK (accepted_quantity BETWEEN 0 AND 1000000),
    rejected_quantity INTEGER NOT NULL CHECK (rejected_quantity BETWEEN 0 AND 1000000),
    rejection_reason VARCHAR(250) NOT NULL DEFAULT '',
    unit_cost NUMERIC(18,2) NOT NULL CHECK (unit_cost BETWEEN 0 AND 999999999999.99),
    accepted_amount NUMERIC(18,2) NOT NULL CHECK (accepted_amount BETWEEN 0 AND 999999999999.99),
    stock_receipt_id BIGINT UNIQUE REFERENCES stock_receipt(id),
    notes VARCHAR(250) NOT NULL DEFAULT '',
    CONSTRAINT uq_purchase_receipt_line UNIQUE(receipt_id,line_number),
    CONSTRAINT uq_purchase_receipt_order_line UNIQUE(receipt_id,order_line_id),
    CONSTRAINT ck_purchase_receipt_positive CHECK (accepted_quantity + rejected_quantity BETWEEN 1 AND 1000000),
    CONSTRAINT ck_purchase_receipt_rejection_reason CHECK (rejected_quantity = 0 OR length(trim(rejection_reason)) > 0),
    CONSTRAINT ck_purchase_receipt_amount CHECK (accepted_amount = unit_cost * accepted_quantity),
    CONSTRAINT ck_purchase_receipt_inventory CHECK ((accepted_quantity > 0) = (stock_receipt_id IS NOT NULL))
);
CREATE INDEX ix_purchase_receipt_line_order ON purchase_receipt_line(order_line_id);
