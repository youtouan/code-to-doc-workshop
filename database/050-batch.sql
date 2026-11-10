-- Batch-only mappings are loaded by the standalone batch module, not by the WAR.
-- Apply after the core schema. A RUNNING job is never automatically assumed complete.
CREATE TABLE batch_run (
    id BIGINT PRIMARY KEY DEFAULT nextval('wholesale_seq'),
    version INTEGER NOT NULL DEFAULT 0,
    run_key VARCHAR(120) NOT NULL UNIQUE,
    command VARCHAR(40) NOT NULL,
    invocation VARCHAR(4000) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    input_sha256 VARCHAR(64) CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    actor_id BIGINT NOT NULL REFERENCES app_user(id),
    actor_login VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','PARTIAL','FAILED')),
    total_rows INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    success_rows INTEGER NOT NULL DEFAULT 0 CHECK (success_rows >= 0),
    rejected_rows INTEGER NOT NULL DEFAULT 0 CHECK (rejected_rows >= 0),
    has_more BOOLEAN NOT NULL DEFAULT FALSE,
    exit_code INTEGER NOT NULL DEFAULT 0 CHECK (exit_code IN (0,1,2,3)),
    message VARCHAR(2000) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    CHECK (total_rows = success_rows + rejected_rows),
    CHECK ((status = 'RUNNING' AND ended_at IS NULL) OR (status <> 'RUNNING' AND ended_at IS NOT NULL))
);
CREATE TABLE batch_row (
    id BIGINT PRIMARY KEY DEFAULT nextval('wholesale_seq'),
    version INTEGER NOT NULL DEFAULT 0,
    run_id BIGINT NOT NULL REFERENCES batch_run(id),
    row_number INTEGER NOT NULL CHECK (row_number > 0),
    source_line BIGINT NOT NULL CHECK (source_line > 0),
    payload_hash VARCHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    result VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS','REJECTED')),
    code VARCHAR(120) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    entity_reference VARCHAR(120),
    finished_at TIMESTAMP NOT NULL,
    UNIQUE (run_id, row_number)
);
CREATE INDEX batch_run_started_idx ON batch_run (started_at, id);
CREATE INDEX batch_run_status_idx ON batch_run (status, id);
