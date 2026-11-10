CREATE TABLE billing_invoice (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    customer_name VARCHAR(160) NOT NULL,
    billing_address VARCHAR(300) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','FINALIZED','VOID')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL CHECK (period_end >= period_start),
    issued_date DATE NOT NULL CHECK (issued_date >= period_end),
    due_date DATE NOT NULL CHECK (due_date >= period_end),
    tax_rounding VARCHAR(10) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    payment_term_days INTEGER NOT NULL CHECK (payment_term_days BETWEEN 0 AND 365),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    total_amount NUMERIC(18,2) NOT NULL CHECK (total_amount = net_amount + tax_amount),
    paid_amount NUMERIC(18,2) NOT NULL CHECK (paid_amount >= 0),
    credited_amount NUMERIC(18,2) NOT NULL CHECK (credited_amount >= 0),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    finalized_by VARCHAR(50),
    finalized_at TIMESTAMP,
    cancelled_by VARCHAR(50),
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    claim_fingerprint VARCHAR(64) NOT NULL,
    CHECK (paid_amount + credited_amount <= total_amount),
    CHECK ((status = 'FINALIZED' AND finalized_by IS NOT NULL AND finalized_at IS NOT NULL)
        OR (status <> 'FINALIZED' AND finalized_by IS NULL AND finalized_at IS NULL)),
    CHECK ((status = 'VOID' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
        AND cancellation_reason IS NOT NULL) OR (status <> 'VOID' AND cancelled_at IS NULL))
);
CREATE UNIQUE INDEX billing_invoice_live_period_uq ON billing_invoice(customer_id, period_end) WHERE status <> 'VOID';
CREATE UNIQUE INDEX billing_invoice_one_draft_uq ON billing_invoice(customer_id) WHERE status = 'DRAFT';
CREATE INDEX billing_invoice_ageing_idx ON billing_invoice(customer_id, status, due_date);
CREATE INDEX billing_invoice_statement_idx ON billing_invoice(customer_id, issued_date) WHERE status = 'FINALIZED';

CREATE TABLE billing_invoice_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    invoice_id BIGINT NOT NULL REFERENCES billing_invoice(id),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    shipment_line_id BIGINT NOT NULL REFERENCES shipping_shipment_line(id),
    shipment_number VARCHAR(50) NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    shipped_date DATE NOT NULL,
    product_code VARCHAR(30) NOT NULL,
    description VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate BETWEEN 0 AND 1),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount = quantity * unit_price),
    UNIQUE(invoice_id, line_number),
    UNIQUE(invoice_id, shipment_line_id)
);
CREATE INDEX billing_invoice_line_source_idx ON billing_invoice_line(shipment_line_id);
ALTER TABLE shipping_shipment ADD CONSTRAINT shipping_shipment_invoice_fk FOREIGN KEY (invoice_id) REFERENCES billing_invoice(id);
CREATE INDEX billing_unbilled_shipment_idx ON shipping_shipment(shipped_date, order_id) WHERE status = 'CONFIRMED' AND invoice_id IS NULL;

CREATE TABLE billing_credit_memo (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    customer_name VARCHAR(160) NOT NULL,
    sales_return_id BIGINT NOT NULL UNIQUE REFERENCES sales_return(id),
    invoice_id BIGINT REFERENCES billing_invoice(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPLIED')),
    issued_date DATE NOT NULL,
    posted_date DATE,
    tax_rounding VARCHAR(10) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    total_amount NUMERIC(18,2) NOT NULL CHECK (total_amount = net_amount + tax_amount),
    applied_amount NUMERIC(18,2) NOT NULL CHECK (applied_amount BETWEEN 0 AND total_amount),
    source_fingerprint VARCHAR(64) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CHECK ((status='APPLIED' AND posted_date IS NOT NULL AND invoice_id IS NOT NULL AND posted_date>=issued_date)
        OR (status='PENDING' AND posted_date IS NULL))
);
CREATE INDEX billing_credit_invoice_idx ON billing_credit_memo(invoice_id);
CREATE INDEX billing_credit_statement_idx ON billing_credit_memo(customer_id, posted_date);

CREATE TABLE billing_credit_memo_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    credit_memo_id BIGINT NOT NULL REFERENCES billing_credit_memo(id),
    sales_return_line_id BIGINT NOT NULL UNIQUE REFERENCES sales_return_line(id),
    shipment_line_id BIGINT NOT NULL REFERENCES shipping_shipment_line(id),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    product_code VARCHAR(30) NOT NULL,
    description VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate BETWEEN 0 AND 1),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount = quantity * unit_price),
    UNIQUE(credit_memo_id, line_number)
);
CREATE INDEX billing_credit_line_source_idx ON billing_credit_memo_line(shipment_line_id);

CREATE TABLE billing_payment_receipt (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    customer_name VARCHAR(160) NOT NULL,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    payload_fingerprint VARCHAR(64) NOT NULL,
    received_date DATE NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    allocated_amount NUMERIC(18,2) NOT NULL CHECK (allocated_amount BETWEEN 0 AND amount),
    method VARCHAR(30) NOT NULL CHECK (method IN ('BANK_TRANSFER','CASH','CHEQUE')),
    reference VARCHAR(100) NOT NULL,
    notes VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('POSTED','CANCELLED')),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    cancelled_by VARCHAR(50),
    cancelled_at TIMESTAMP,
    cancellation_date DATE,
    cancellation_reason VARCHAR(500),
    CHECK ((status='CANCELLED' AND allocated_amount=0 AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
        AND cancellation_date>=received_date AND cancellation_reason IS NOT NULL)
        OR (status='POSTED' AND cancelled_at IS NULL AND cancellation_date IS NULL))
);
CREATE INDEX billing_receipt_customer_date_idx ON billing_payment_receipt(customer_id, received_date);
CREATE INDEX billing_receipt_cancel_date_idx ON billing_payment_receipt(customer_id, cancellation_date);

CREATE TABLE billing_payment_allocation (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    receipt_id BIGINT NOT NULL REFERENCES billing_payment_receipt(id),
    invoice_id BIGINT NOT NULL REFERENCES billing_invoice(id),
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    allocation_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REVERSED')),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    reversed_by VARCHAR(50),
    reversed_at TIMESTAMP,
    reversal_date DATE,
    reversal_reason VARCHAR(500),
    CHECK ((status='REVERSED' AND reversed_by IS NOT NULL AND reversed_at IS NOT NULL
        AND reversal_date>=allocation_date AND reversal_reason IS NOT NULL)
        OR (status='ACTIVE' AND reversed_at IS NULL AND reversal_date IS NULL))
);
CREATE INDEX billing_allocation_receipt_idx ON billing_payment_allocation(receipt_id, status);
CREATE INDEX billing_allocation_invoice_date_idx ON billing_payment_allocation(invoice_id, allocation_date, reversal_date);

CREATE FUNCTION billing_freeze_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Invoice documents cannot be deleted';
    END IF;
    IF OLD.status <> 'DRAFT' AND
       (to_jsonb(NEW) - ARRAY['version','paid_amount','credited_amount']) IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['version','paid_amount','credited_amount']) THEN
        RAISE EXCEPTION 'Finalized and void invoice facts are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER billing_freeze_invoice_trigger BEFORE UPDATE OR DELETE ON billing_invoice
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_invoice();

CREATE FUNCTION billing_freeze_invoice_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_id BIGINT;
    parent_status VARCHAR(20);
BEGIN
    parent_id := CASE WHEN TG_OP='INSERT' THEN NEW.invoice_id ELSE OLD.invoice_id END;
    SELECT status INTO parent_status FROM billing_invoice WHERE id=parent_id;
    IF parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Only draft invoice lines may be changed';
    END IF;
    IF TG_OP='UPDATE' AND NEW.invoice_id <> OLD.invoice_id THEN
        RAISE EXCEPTION 'Invoice line ownership cannot change';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER billing_freeze_invoice_line_trigger BEFORE INSERT OR UPDATE OR DELETE ON billing_invoice_line
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_invoice_line();

CREATE FUNCTION billing_freeze_credit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Return credit documents cannot be deleted'; END IF;
    IF OLD.status='APPLIED' AND
       (to_jsonb(NEW) - ARRAY['version','applied_amount']) IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['version','applied_amount']) THEN
        RAISE EXCEPTION 'Posted credit facts are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER billing_freeze_credit_trigger BEFORE UPDATE OR DELETE ON billing_credit_memo
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_credit();

CREATE FUNCTION billing_freeze_credit_line() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF EXISTS (SELECT 1 FROM billing_credit_memo WHERE id=NEW.credit_memo_id AND status='APPLIED') THEN
            RAISE EXCEPTION 'Posted credit cannot accept additional source lines';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Return credit source lines are immutable';
END;
$$;
CREATE TRIGGER billing_freeze_credit_line_trigger BEFORE INSERT OR UPDATE OR DELETE ON billing_credit_memo_line
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_credit_line();

CREATE FUNCTION billing_freeze_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Receipt ledger rows cannot be deleted'; END IF;
    IF ROW(NEW.customer_id,NEW.request_key,NEW.payload_fingerprint,NEW.received_date,NEW.amount,
           NEW.method,NEW.reference,NEW.notes,NEW.created_by,NEW.created_at) IS DISTINCT FROM
       ROW(OLD.customer_id,OLD.request_key,OLD.payload_fingerprint,OLD.received_date,OLD.amount,
           OLD.method,OLD.reference,OLD.notes,OLD.created_by,OLD.created_at) THEN
        RAISE EXCEPTION 'Posted receipt facts are immutable';
    END IF;
    IF OLD.status='CANCELLED' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Cancelled receipt is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER billing_freeze_receipt_trigger BEFORE UPDATE OR DELETE ON billing_payment_receipt
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_receipt();

CREATE FUNCTION billing_freeze_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Allocation ledger rows cannot be deleted'; END IF;
    IF ROW(NEW.receipt_id,NEW.invoice_id,NEW.amount,NEW.allocation_date,NEW.created_by,NEW.created_at) IS DISTINCT FROM
       ROW(OLD.receipt_id,OLD.invoice_id,OLD.amount,OLD.allocation_date,OLD.created_by,OLD.created_at) THEN
        RAISE EXCEPTION 'Allocation facts are immutable; reverse instead';
    END IF;
    IF OLD.status='REVERSED' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Reversed allocation is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER billing_freeze_allocation_trigger BEFORE UPDATE OR DELETE ON billing_payment_allocation
    FOR EACH ROW EXECUTE FUNCTION billing_freeze_allocation();
