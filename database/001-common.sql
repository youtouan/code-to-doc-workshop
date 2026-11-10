CREATE SEQUENCE wholesale_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE audit_event (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL,
    actor VARCHAR(50) NOT NULL,
    operation VARCHAR(60) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    detail VARCHAR(1000) NOT NULL
);
CREATE INDEX audit_event_entity_idx ON audit_event(entity_type, entity_id, occurred_at);
