CREATE TABLE quotation (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    revision_number INTEGER NOT NULL CHECK (revision_number BETWEEN 1 AND 100),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','ACCEPTED','WITHDRAWN','REJECTED','EXPIRED','CANCELLED','CONVERTED')),
    created_by_id BIGINT NOT NULL REFERENCES app_user(id),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    submitted_by_id BIGINT REFERENCES app_user(id),
    submitted_by VARCHAR(50),
    submitted_at TIMESTAMP,
    approved_by_id BIGINT REFERENCES app_user(id),
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    approved_fingerprint VARCHAR(64),
    accepted_by_id BIGINT REFERENCES app_user(id),
    accepted_by VARCHAR(50),
    accepted_at TIMESTAMP,
    accepted_on DATE,
    acceptance_reference VARCHAR(120) NOT NULL DEFAULT '',
    converted_order_id BIGINT UNIQUE REFERENCES sales_order(id),
    converted_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT quotation_approval_identity CHECK (approved_by_id IS NULL OR
        (approved_by_id <> created_by_id AND approved_by_id <> submitted_by_id)),
    CONSTRAINT quotation_approved_state CHECK (status NOT IN ('APPROVED','ACCEPTED','CONVERTED') OR
        (approved_by_id IS NOT NULL AND submitted_by_id IS NOT NULL AND approved_at IS NOT NULL
         AND approved_fingerprint ~ '^[0-9a-f]{64}$')),
    CONSTRAINT quotation_acceptance_state CHECK (status NOT IN ('ACCEPTED','CONVERTED') OR
        (accepted_by_id IS NOT NULL AND accepted_at IS NOT NULL AND accepted_on IS NOT NULL
         AND length(trim(acceptance_reference)) > 0)),
    CONSTRAINT quotation_conversion_state CHECK
        ((status = 'CONVERTED') = (converted_order_id IS NOT NULL AND converted_at IS NOT NULL))
);
CREATE INDEX quotation_search_idx ON quotation(customer_id,status,id);
CREATE INDEX quotation_warehouse_idx ON quotation(warehouse_id,status);

CREATE TABLE quotation_revision (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    quotation_id BIGINT NOT NULL REFERENCES quotation(id),
    revision_number INTEGER NOT NULL CHECK (revision_number BETWEEN 1 AND 100),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    customer_name VARCHAR(160) NOT NULL,
    quote_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    requested_date DATE NOT NULL,
    delivery_address VARCHAR(300) NOT NULL,
    external_reference VARCHAR(80) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    tax_rounding VARCHAR(10) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    authored_by_id BIGINT NOT NULL REFERENCES app_user(id),
    authored_by VARCHAR(50) NOT NULL,
    authored_at TIMESTAMP NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    CONSTRAINT quotation_revision_unique UNIQUE(quotation_id,revision_number),
    CONSTRAINT quotation_revision_dates CHECK
        (valid_until BETWEEN quote_date AND quote_date + 180
         AND requested_date BETWEEN quote_date AND quote_date + 365)
);
ALTER TABLE quotation ADD CONSTRAINT quotation_current_revision_fk
    FOREIGN KEY(id,revision_number) REFERENCES quotation_revision(quotation_id,revision_number)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX quotation_revision_expiry_idx ON quotation_revision(valid_until,quotation_id);
CREATE INDEX quotation_revision_warehouse_idx ON quotation_revision(warehouse_id);

CREATE TABLE quotation_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    revision_id BIGINT NOT NULL REFERENCES quotation_revision(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    product_id BIGINT NOT NULL REFERENCES product(id),
    product_code VARCHAR(30) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    pack_size INTEGER NOT NULL CHECK (pack_size BETWEEN 1 AND 1000000),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    catalog_unit_price NUMERIC(18,2) NOT NULL CHECK (catalog_unit_price BETWEEN 0 AND 999999999999.99),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price BETWEEN 0 AND 999999999999.99),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate IN (0.0000,0.0800,0.1000)),
    negotiation_reason VARCHAR(300) NOT NULL DEFAULT '',
    negotiated BOOLEAN NOT NULL,
    CONSTRAINT quotation_line_position UNIQUE(revision_id,line_number),
    CONSTRAINT quotation_line_product UNIQUE(revision_id,product_id),
    CONSTRAINT quotation_line_pack CHECK (quantity % pack_size = 0),
    CONSTRAINT quotation_line_negotiation CHECK
        ((negotiated AND length(trim(negotiation_reason)) > 0)
         OR (NOT negotiated AND unit_price = catalog_unit_price))
);
CREATE INDEX quotation_line_product_idx ON quotation_line(product_id,revision_id);

CREATE TABLE quotation_event (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    quotation_id BIGINT NOT NULL REFERENCES quotation(id),
    revision_number INTEGER NOT NULL,
    operation VARCHAR(40) NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL DEFAULT '',
    fingerprint VARCHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    actor VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT quotation_event_revision_fk FOREIGN KEY(quotation_id,revision_number)
        REFERENCES quotation_revision(quotation_id,revision_number)
);
CREATE INDEX quotation_event_history_idx ON quotation_event(quotation_id,id);

CREATE TABLE order_amendment (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES sales_order(id),
    base_order_version INTEGER NOT NULL CHECK (base_order_version >= 0),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','APPLIED','REJECTED','CANCELLED')),
    original_requested_date DATE NOT NULL,
    requested_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    requested_by_id BIGINT NOT NULL REFERENCES app_user(id),
    requested_by VARCHAR(50) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    decided_by_id BIGINT REFERENCES app_user(id),
    decided_by VARCHAR(50),
    decided_at TIMESTAMP,
    decision_reason VARCHAR(500) NOT NULL DEFAULT '',
    original_net_amount NUMERIC(18,2) NOT NULL CHECK (original_net_amount >= 0),
    original_tax_amount NUMERIC(18,2) NOT NULL CHECK (original_tax_amount >= 0),
    proposed_net_amount NUMERIC(18,2) NOT NULL CHECK (proposed_net_amount >= 0),
    proposed_tax_amount NUMERIC(18,2) NOT NULL CHECK (proposed_tax_amount >= 0),
    exposure_delta NUMERIC(18,2) NOT NULL,
    applied_order_version INTEGER,
    CONSTRAINT order_amendment_applied CHECK (status <> 'APPLIED' OR
        (decided_by_id IS NOT NULL AND decided_by_id <> requested_by_id
         AND decided_at IS NOT NULL AND applied_order_version IS NOT NULL)),
    CONSTRAINT order_amendment_decision CHECK (status = 'REQUESTED' OR
        (decided_by_id IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE UNIQUE INDEX order_amendment_pending_idx ON order_amendment(order_id) WHERE status = 'REQUESTED';
CREATE INDEX order_amendment_search_idx ON order_amendment(status,requested_at,id);
CREATE INDEX order_amendment_order_idx ON order_amendment(order_id,id);

CREATE TABLE order_amendment_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    amendment_id BIGINT NOT NULL REFERENCES order_amendment(id),
    order_line_id BIGINT NOT NULL REFERENCES sales_order_line(id),
    original_quantity INTEGER NOT NULL CHECK (original_quantity BETWEEN 1 AND 1000000),
    target_quantity INTEGER NOT NULL CHECK (target_quantity BETWEEN 1 AND 1000000),
    original_allocated_quantity INTEGER NOT NULL CHECK (original_allocated_quantity >= 0),
    original_shipped_quantity INTEGER NOT NULL CHECK (original_shipped_quantity >= 0),
    original_cancelled_quantity INTEGER NOT NULL CHECK (original_cancelled_quantity >= 0),
    pack_size INTEGER NOT NULL CHECK (pack_size BETWEEN 1 AND 1000000),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price BETWEEN 0 AND 999999999999.99),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate IN (0.0000,0.0800,0.1000)),
    CONSTRAINT order_amendment_line_unique UNIQUE(amendment_id,order_line_id),
    CONSTRAINT order_amendment_fulfilled CHECK
        (target_quantity >= original_shipped_quantity + original_cancelled_quantity),
    CONSTRAINT order_amendment_pack CHECK (target_quantity % pack_size = 0),
    CONSTRAINT order_amendment_changed CHECK (target_quantity <> original_quantity)
);
CREATE INDEX order_amendment_line_order_idx ON order_amendment_line(order_line_id);
