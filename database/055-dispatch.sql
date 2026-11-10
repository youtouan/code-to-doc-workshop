CREATE TABLE dispatch_manifest (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    carrier VARCHAR(30) NOT NULL CHECK (carrier IN ('OWN','PARCEL','FREIGHT')),
    planned_dispatch_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','RELEASED','DISPATCHED','CANCELLED')),
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    released_by VARCHAR(50),
    released_at TIMESTAMP,
    dispatched_by VARCHAR(50),
    dispatched_at TIMESTAMP,
    dispatch_date DATE,
    cancellation_reason VARCHAR(500) NOT NULL DEFAULT '',
    CHECK (status<>'RELEASED' OR released_at IS NOT NULL),
    CHECK (status<>'DISPATCHED' OR (dispatch_date IS NOT NULL AND dispatched_at IS NOT NULL)),
    CHECK (status<>'CANCELLED' OR length(trim(cancellation_reason))>0)
);
CREATE INDEX dispatch_manifest_queue_idx ON dispatch_manifest(warehouse_id,status,planned_dispatch_date,id);

CREATE TABLE dispatch_stop (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    manifest_id BIGINT NOT NULL REFERENCES dispatch_manifest(id),
    shipment_id BIGINT NOT NULL REFERENCES shipping_shipment(id),
    stop_sequence INTEGER NOT NULL CHECK (stop_sequence BETWEEN 1 AND 100),
    source_shipment_version INTEGER NOT NULL CHECK (source_shipment_version>=0),
    source_order_version INTEGER NOT NULL CHECK (source_order_version>=0),
    source_fingerprint VARCHAR(64) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    delivery_address VARCHAR(300) NOT NULL,
    note VARCHAR(250) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(manifest_id,stop_sequence),
    UNIQUE(manifest_id,shipment_id)
);
CREATE UNIQUE INDEX dispatch_stop_active_shipment_idx ON dispatch_stop(shipment_id) WHERE active=TRUE;

CREATE TABLE delivery_attempt (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    shipment_id BIGINT NOT NULL REFERENCES shipping_shipment(id),
    sequence_number INTEGER NOT NULL CHECK (sequence_number>0),
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('ATTEMPT','CORRECTION','REVERSAL')),
    supersedes_id BIGINT UNIQUE REFERENCES delivery_attempt(id),
    request_key VARCHAR(120) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    attempt_at TIMESTAMP NOT NULL,
    business_date DATE NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('DELIVERED','FAILED','RESCHEDULED','REVERSED')),
    reporting_company VARCHAR(120) NOT NULL,
    evidence_reference VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL DEFAULT '',
    next_attempt_date DATE,
    correction_reason VARCHAR(500) NOT NULL DEFAULT '',
    recorded_by VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    UNIQUE(shipment_id,sequence_number),
    CHECK ((event_type='ATTEMPT' AND supersedes_id IS NULL)
        OR (event_type IN ('CORRECTION','REVERSAL') AND supersedes_id IS NOT NULL AND length(trim(correction_reason))>0)),
    CHECK ((event_type='REVERSAL' AND outcome='REVERSED') OR (event_type<>'REVERSAL' AND outcome<>'REVERSED')),
    CHECK (outcome NOT IN ('FAILED','RESCHEDULED') OR length(trim(reason))>0),
    CHECK ((outcome='RESCHEDULED' AND next_attempt_date IS NOT NULL AND next_attempt_date>business_date)
        OR (outcome<>'RESCHEDULED' AND next_attempt_date IS NULL))
);
CREATE INDEX delivery_attempt_effective_idx ON delivery_attempt(shipment_id,attempt_at DESC,id DESC);
CREATE INDEX delivery_attempt_recorded_idx ON delivery_attempt(recorded_at,id);
CREATE INDEX delivery_attempt_queue_idx ON delivery_attempt(outcome,business_date,next_attempt_date);
