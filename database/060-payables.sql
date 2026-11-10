CREATE TABLE ap_invoice (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    supplier_name VARCHAR(160) NOT NULL,
    supplier_address VARCHAR(300) NOT NULL,
    supplier_invoice_number VARCHAR(80) NOT NULL CHECK (length(trim(supplier_invoice_number)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL CHECK (due_date >= invoice_date AND due_date <= invoice_date + 365),
    posted_date DATE,
    tax_rounding VARCHAR(10) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    total_amount NUMERIC(18,2) NOT NULL CHECK (total_amount = net_amount + tax_amount),
    paid_amount NUMERIC(18,2) NOT NULL CHECK (paid_amount >= 0),
    credited_amount NUMERIC(18,2) NOT NULL CHECK (credited_amount >= 0),
    variance_amount NUMERIC(18,2) NOT NULL,
    absolute_variance_amount NUMERIC(18,2) NOT NULL CHECK (absolute_variance_amount >= abs(variance_amount)),
    variance_status VARCHAR(20) NOT NULL CHECK (variance_status IN ('NONE','REQUIRED','APPROVED')),
    variance_reason VARCHAR(500) NOT NULL,
    variance_approved_by VARCHAR(50),
    variance_approved_by_id BIGINT REFERENCES app_user(id),
    variance_approved_at TIMESTAMP,
    review_fingerprint VARCHAR(64),
    change_number INTEGER NOT NULL CHECK (change_number > 0),
    notes VARCHAR(1000) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_by_id BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL,
    last_changed_by VARCHAR(50) NOT NULL,
    last_changed_by_id BIGINT NOT NULL REFERENCES app_user(id),
    last_changed_at TIMESTAMP NOT NULL,
    posted_by VARCHAR(50),
    posted_at TIMESTAMP,
    cancelled_by VARCHAR(50),
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    UNIQUE (supplier_id, supplier_invoice_number),
    CHECK (paid_amount + credited_amount <= total_amount),
    CHECK ((status='POSTED' AND posted_date>=invoice_date AND posted_by IS NOT NULL AND posted_at IS NOT NULL)
        OR (status<>'POSTED' AND posted_date IS NULL AND posted_at IS NULL)),
    CHECK (status<>'POSTED' OR absolute_variance_amount=0 OR variance_status='APPROVED'),
    CHECK ((variance_status='APPROVED' AND variance_approved_by IS NOT NULL AND variance_approved_at IS NOT NULL
        AND review_fingerprint IS NOT NULL AND length(trim(variance_reason))>0
        AND variance_approved_by_id IS NOT NULL
        AND variance_approved_by_id<>created_by_id AND variance_approved_by_id<>last_changed_by_id)
        OR (variance_status<>'APPROVED' AND variance_approved_by IS NULL AND variance_approved_by_id IS NULL AND variance_approved_at IS NULL)),
    CHECK ((status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status<>'CANCELLED' AND cancelled_at IS NULL))
);
CREATE INDEX ap_invoice_due_idx ON ap_invoice(supplier_id, due_date, id) WHERE status='POSTED';
CREATE INDEX ap_invoice_variance_idx ON ap_invoice(due_date, id) WHERE status='DRAFT' AND variance_status='REQUIRED';

CREATE TABLE ap_invoice_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    invoice_id BIGINT NOT NULL REFERENCES ap_invoice(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    product_id BIGINT NOT NULL REFERENCES product(id),
    product_code VARCHAR(30) NOT NULL,
    description VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate BETWEEN 0 AND 1),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount = quantity * unit_price),
    UNIQUE(invoice_id, line_number)
);
CREATE TABLE ap_match (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    invoice_line_id BIGINT NOT NULL REFERENCES ap_invoice_line(id),
    receipt_line_id BIGINT NOT NULL REFERENCES purchase_receipt_line(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    receipt_number VARCHAR(50) NOT NULL,
    purchase_order_number VARCHAR(50) NOT NULL,
    receipt_date DATE NOT NULL,
    receipt_unit_cost NUMERIC(18,2) NOT NULL CHECK (receipt_unit_cost >= 0),
    ordered_unit_cost NUMERIC(18,2) NOT NULL CHECK (ordered_unit_cost >= 0),
    UNIQUE(invoice_line_id, receipt_line_id)
);
CREATE INDEX ap_match_receipt_idx ON ap_match(receipt_line_id);

CREATE TABLE ap_credit (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    invoice_id BIGINT NOT NULL REFERENCES ap_invoice(id),
    supplier_credit_number VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    credit_date DATE NOT NULL,
    posted_date DATE,
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason))>0),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount>0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount>=0),
    total_amount NUMERIC(18,2) NOT NULL CHECK (total_amount=net_amount+tax_amount),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    approved_by VARCHAR(50),
    created_by_id BIGINT NOT NULL REFERENCES app_user(id),
    approved_by_id BIGINT REFERENCES app_user(id),
    approved_at TIMESTAMP,
    cancelled_by VARCHAR(50),
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    UNIQUE(supplier_id,supplier_credit_number),
    CHECK ((status='POSTED' AND posted_date>=credit_date AND approved_by IS NOT NULL
        AND approved_by_id IS NOT NULL AND approved_by_id<>created_by_id AND approved_at IS NOT NULL)
        OR (status<>'POSTED' AND posted_date IS NULL AND approved_at IS NULL)),
    CHECK ((status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status<>'CANCELLED' AND cancelled_at IS NULL))
);
CREATE INDEX ap_credit_invoice_idx ON ap_credit(invoice_id,status,posted_date);
CREATE TABLE ap_credit_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    credit_id BIGINT NOT NULL REFERENCES ap_credit(id),
    invoice_line_id BIGINT NOT NULL REFERENCES ap_invoice_line(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    description VARCHAR(160) NOT NULL,
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount>0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate BETWEEN 0 AND 1),
    UNIQUE(credit_id,line_number),
    UNIQUE(credit_id,invoice_line_id)
);
CREATE INDEX ap_credit_claim_idx ON ap_credit_line(invoice_line_id);

CREATE TABLE ap_payment_voucher (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES supplier(id),
    supplier_name VARCHAR(160) NOT NULL,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    payment_date DATE NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0),
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
    CHECK ((status='CANCELLED' AND cancellation_date>=payment_date AND cancelled_by IS NOT NULL
        AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status='POSTED' AND cancellation_date IS NULL AND cancelled_at IS NULL))
);
CREATE INDEX ap_payment_supplier_date_idx ON ap_payment_voucher(supplier_id,payment_date);
CREATE INDEX ap_payment_cancellation_idx ON ap_payment_voucher(supplier_id,cancellation_date);
CREATE TABLE ap_payment_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    voucher_id BIGINT NOT NULL REFERENCES ap_payment_voucher(id),
    invoice_id BIGINT NOT NULL REFERENCES ap_invoice(id),
    amount NUMERIC(18,2) NOT NULL CHECK (amount>0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REVERSED')),
    reversal_date DATE,
    UNIQUE(voucher_id,invoice_id),
    CHECK ((status='ACTIVE' AND reversal_date IS NULL) OR (status='REVERSED' AND reversal_date IS NOT NULL))
);
CREATE INDEX ap_payment_invoice_idx ON ap_payment_line(invoice_id,status,reversal_date);

CREATE FUNCTION ap_guard_invoice() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'AP invoices are retained; cancel a draft instead'; END IF;
    IF OLD.status<>'DRAFT' AND
       (to_jsonb(NEW)-ARRAY['version','paid_amount','credited_amount']) IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['version','paid_amount','credited_amount']) THEN
        RAISE EXCEPTION 'Posted or cancelled AP facts are immutable';
    END IF;
    IF OLD.status='DRAFT' AND NEW.status='POSTED' THEN
        IF NOT EXISTS (SELECT 1 FROM ap_invoice_line WHERE invoice_id=NEW.id)
           OR EXISTS (SELECT 1 FROM ap_invoice_line l WHERE l.invoice_id=NEW.id
               AND l.quantity<>(SELECT coalesce(sum(m.quantity),0) FROM ap_match m WHERE m.invoice_line_id=l.id)) THEN
            RAISE EXCEPTION 'Posting requires every quantity to match accepted receipts';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_invoice_guard BEFORE UPDATE OR DELETE ON ap_invoice
    FOR EACH ROW EXECUTE FUNCTION ap_guard_invoice();

CREATE FUNCTION ap_guard_invoice_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id BIGINT;
BEGIN
    parent_id := CASE WHEN TG_OP='INSERT' THEN NEW.invoice_id ELSE OLD.invoice_id END;
    IF NOT EXISTS(SELECT 1 FROM ap_invoice WHERE id=parent_id AND status='DRAFT') THEN
        RAISE EXCEPTION 'Only draft AP lines can change';
    END IF;
    IF TG_OP='UPDATE' AND NEW.invoice_id<>OLD.invoice_id THEN RAISE EXCEPTION 'AP line owner cannot change'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_invoice_line_guard BEFORE INSERT OR UPDATE OR DELETE ON ap_invoice_line
    FOR EACH ROW EXECUTE FUNCTION ap_guard_invoice_line();

CREATE FUNCTION ap_guard_match() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE claim RECORD; source RECORD; claimed BIGINT; line_claimed BIGINT; line_id BIGINT;
BEGIN
    line_id := CASE WHEN TG_OP='INSERT' THEN NEW.invoice_line_id ELSE OLD.invoice_line_id END;
    SELECT l.product_id,l.quantity,i.supplier_id,i.status,i.invoice_date INTO claim
        FROM ap_invoice_line l JOIN ap_invoice i ON i.id=l.invoice_id WHERE l.id=line_id;
    IF claim.status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Posted receipt matching is immutable'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF TG_OP='UPDATE' AND (NEW.invoice_line_id<>OLD.invoice_line_id OR NEW.receipt_line_id<>OLD.receipt_line_id) THEN
        RAISE EXCEPTION 'Replace matches rather than changing source ownership';
    END IF;
    PERFORM 1 FROM supplier WHERE id=claim.supplier_id FOR UPDATE;
    SELECT l.accepted_quantity,l.stock_receipt_id,l.unit_cost,r.receipt_date,o.supplier_id,ol.product_id
        INTO source FROM purchase_receipt_line l JOIN purchase_receipt r ON r.id=l.receipt_id
        JOIN purchase_order o ON o.id=r.order_id JOIN purchase_order_line ol ON ol.id=l.order_line_id
        WHERE l.id=NEW.receipt_line_id;
    IF source.supplier_id IS DISTINCT FROM claim.supplier_id OR source.product_id IS DISTINCT FROM claim.product_id
       OR source.stock_receipt_id IS NULL OR source.receipt_date>claim.invoice_date THEN
        RAISE EXCEPTION 'AP match must refer to this supplier/product accepted receipt';
    END IF;
    SELECT coalesce(sum(m.quantity),0) INTO claimed FROM ap_match m JOIN ap_invoice_line l ON l.id=m.invoice_line_id
        JOIN ap_invoice i ON i.id=l.invoice_id WHERE m.receipt_line_id=NEW.receipt_line_id
        AND i.status<>'CANCELLED' AND m.id<>NEW.id;
    SELECT coalesce(sum(quantity),0) INTO line_claimed FROM ap_match WHERE invoice_line_id=line_id AND id<>NEW.id;
    IF claimed+NEW.quantity>source.accepted_quantity OR line_claimed+NEW.quantity>claim.quantity THEN
        RAISE EXCEPTION 'AP match quantity exceeds accepted or invoiced quantity';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_match_guard BEFORE INSERT OR UPDATE OR DELETE ON ap_match
    FOR EACH ROW EXECUTE FUNCTION ap_guard_match();

CREATE FUNCTION ap_guard_credit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source ap_invoice%ROWTYPE; reductions NUMERIC;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'AP credits are retained'; END IF;
    IF TG_OP='UPDATE' AND OLD.status<>'DRAFT' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Posted or cancelled AP credits are immutable';
    END IF;
    SELECT * INTO source FROM ap_invoice WHERE id=NEW.invoice_id;
    IF source.status IS DISTINCT FROM 'POSTED' OR source.supplier_id IS DISTINCT FROM NEW.supplier_id
       OR NEW.credit_date<source.invoice_date THEN RAISE EXCEPTION 'AP credit requires an original posted claim'; END IF;
    PERFORM 1 FROM supplier WHERE id=NEW.supplier_id FOR UPDATE;
    IF NEW.status='POSTED' THEN
        SELECT coalesce(sum(total_amount),0) INTO reductions FROM ap_credit
            WHERE invoice_id=NEW.invoice_id AND status='POSTED' AND id<>NEW.id;
        reductions := reductions+(SELECT coalesce(sum(amount),0) FROM ap_payment_line
            WHERE invoice_id=NEW.invoice_id AND status='ACTIVE');
        IF reductions+NEW.total_amount>source.total_amount THEN RAISE EXCEPTION 'AP credit exceeds remaining liability'; END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_credit_guard BEFORE INSERT OR UPDATE OR DELETE ON ap_credit
    FOR EACH ROW EXECUTE FUNCTION ap_guard_credit();

CREATE FUNCTION ap_guard_credit_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE header ap_credit%ROWTYPE; source ap_invoice_line%ROWTYPE; claimed NUMERIC;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'AP credit source lines are immutable'; END IF;
    SELECT * INTO header FROM ap_credit WHERE id=NEW.credit_id;
    SELECT * INTO source FROM ap_invoice_line WHERE id=NEW.invoice_line_id;
    PERFORM 1 FROM supplier WHERE id=header.supplier_id FOR UPDATE;
    IF header.status IS DISTINCT FROM 'DRAFT' OR header.invoice_id IS DISTINCT FROM source.invoice_id
       OR NEW.tax_rate IS DISTINCT FROM source.tax_rate THEN RAISE EXCEPTION 'Invalid AP credit source'; END IF;
    SELECT coalesce(sum(l.net_amount),0) INTO claimed FROM ap_credit_line l JOIN ap_credit c ON c.id=l.credit_id
        WHERE l.invoice_line_id=NEW.invoice_line_id AND c.status<>'CANCELLED';
    IF claimed+NEW.net_amount>source.net_amount THEN RAISE EXCEPTION 'Cumulative financial credits exceed invoice line'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_credit_line_guard BEFORE INSERT OR UPDATE OR DELETE ON ap_credit_line
    FOR EACH ROW EXECUTE FUNCTION ap_guard_credit_line();

CREATE FUNCTION ap_guard_payment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'AP payment history cannot be deleted'; END IF;
    IF (to_jsonb(NEW)-ARRAY['version','number','status','cancelled_by','cancelled_at','cancellation_date','cancellation_reason'])
        IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['version','number','status','cancelled_by','cancelled_at','cancellation_date','cancellation_reason']) THEN
        RAISE EXCEPTION 'AP payment facts are immutable';
    END IF;
    IF NEW.number<>OLD.number AND OLD.number NOT LIKE 'NEW-%' THEN RAISE EXCEPTION 'AP payment number is frozen'; END IF;
    IF OLD.status='CANCELLED' AND NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Cancelled AP payment is immutable'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_payment_guard BEFORE UPDATE OR DELETE ON ap_payment_voucher
    FOR EACH ROW EXECUTE FUNCTION ap_guard_payment();

CREATE FUNCTION ap_guard_payment_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE voucher ap_payment_voucher%ROWTYPE; invoice ap_invoice%ROWTYPE; reductions NUMERIC;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'AP settlement history cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.voucher_id,NEW.invoice_id,NEW.amount) IS DISTINCT FROM ROW(OLD.voucher_id,OLD.invoice_id,OLD.amount)
           OR OLD.status<>'ACTIVE' OR NEW.status<>'REVERSED' THEN
            RAISE EXCEPTION 'AP settlements may only be reversed';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO voucher FROM ap_payment_voucher WHERE id=NEW.voucher_id;
    PERFORM 1 FROM supplier WHERE id=voucher.supplier_id FOR UPDATE;
    SELECT * INTO invoice FROM ap_invoice WHERE id=NEW.invoice_id;
    IF invoice.supplier_id IS DISTINCT FROM voucher.supplier_id OR invoice.status IS DISTINCT FROM 'POSTED'
       OR voucher.status IS DISTINCT FROM 'POSTED' OR voucher.payment_date<invoice.posted_date THEN
        RAISE EXCEPTION 'Settlement requires a posted claim for the voucher supplier';
    END IF;
    SELECT coalesce(sum(amount),0) INTO reductions FROM ap_payment_line WHERE invoice_id=NEW.invoice_id AND status='ACTIVE';
    reductions := reductions+(SELECT coalesce(sum(total_amount),0) FROM ap_credit WHERE invoice_id=NEW.invoice_id AND status='POSTED');
    IF reductions+NEW.amount>invoice.total_amount THEN RAISE EXCEPTION 'AP settlement exceeds liability'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ap_payment_line_guard BEFORE INSERT OR UPDATE OR DELETE ON ap_payment_line
    FOR EACH ROW EXECUTE FUNCTION ap_guard_payment_line();

CREATE FUNCTION ap_validate_voucher(voucher_id_to_check BIGINT) RETURNS void LANGUAGE plpgsql AS $$
DECLARE voucher ap_payment_voucher%ROWTYPE; settled NUMERIC; invalid_count BIGINT;
BEGIN
    SELECT * INTO voucher FROM ap_payment_voucher WHERE id=voucher_id_to_check;
    SELECT coalesce(sum(amount),0),count(*) FILTER (WHERE
        (voucher.status='POSTED' AND status<>'ACTIVE') OR
        (voucher.status='CANCELLED' AND (status<>'REVERSED' OR reversal_date IS DISTINCT FROM voucher.cancellation_date)))
        INTO settled,invalid_count FROM ap_payment_line WHERE voucher_id=voucher_id_to_check;
    IF settled<>voucher.amount OR invalid_count<>0 THEN RAISE EXCEPTION 'AP voucher total or reversal is incomplete'; END IF;
END;
$$;
CREATE FUNCTION ap_check_voucher_total() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM ap_validate_voucher(NEW.id);
    RETURN NULL;
END;
$$;
CREATE FUNCTION ap_check_settlement_total() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM ap_validate_voucher(NEW.voucher_id);
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER ap_voucher_total AFTER INSERT OR UPDATE ON ap_payment_voucher
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ap_check_voucher_total();
CREATE CONSTRAINT TRIGGER ap_settlement_total AFTER INSERT OR UPDATE ON ap_payment_line
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ap_check_settlement_total();
