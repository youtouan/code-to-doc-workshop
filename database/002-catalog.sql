CREATE TABLE customer (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    code VARCHAR(30) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,29}$'),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    on_hold BOOLEAN NOT NULL DEFAULT FALSE,
    credit_limit NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (credit_limit BETWEEN 0 AND 999999999999.99),
    closing_day INTEGER NOT NULL DEFAULT 31 CHECK (closing_day IN (10,20,31)),
    payment_term_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_term_days BETWEEN 0 AND 120),
    tax_rounding VARCHAR(10) NOT NULL DEFAULT 'DOWN' CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    postal_code VARCHAR(12) NOT NULL DEFAULT '',
    address VARCHAR(250) NOT NULL DEFAULT '',
    telephone VARCHAR(30) NOT NULL DEFAULT '',
    notes VARCHAR(1000) NOT NULL DEFAULT ''
);
CREATE INDEX ix_customer_active_code ON customer(active,code);
CREATE INDEX ix_customer_closing ON customer(closing_day,active);

CREATE TABLE product (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    code VARCHAR(30) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,29}$'),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    unit VARCHAR(20) NOT NULL CHECK (length(trim(unit)) > 0),
    tax_category VARCHAR(10) NOT NULL DEFAULT 'STANDARD' CHECK (tax_category IN ('STANDARD','REDUCED','EXEMPT')),
    list_price NUMERIC(18,2) NOT NULL CHECK (list_price BETWEEN 0 AND 999999999999.99),
    standard_cost NUMERIC(18,2) NOT NULL CHECK (standard_cost BETWEEN 0 AND 999999999999.99),
    pack_size INTEGER NOT NULL DEFAULT 1 CHECK (pack_size BETWEEN 1 AND 1000000),
    reorder_point INTEGER NOT NULL DEFAULT 0 CHECK (reorder_point BETWEEN 0 AND 1000000),
    reorder_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reorder_quantity BETWEEN 0 AND 1000000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT ck_product_reorder_pack CHECK (reorder_quantity % pack_size = 0)
);
CREATE INDEX ix_product_active_code ON product(active,code);

CREATE TABLE warehouse (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    code VARCHAR(30) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,29}$'),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    address VARCHAR(250) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE price_agreement (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    valid_from DATE NOT NULL,
    valid_to DATE,
    minimum_quantity INTEGER NOT NULL DEFAULT 1 CHECK (minimum_quantity BETWEEN 1 AND 1000000),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price BETWEEN 0 AND 999999999999.99),
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT ck_price_agreement_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT uq_price_agreement_tier_start UNIQUE(customer_id,product_id,minimum_quantity,valid_from)
);
CREATE INDEX ix_price_agreement_lookup ON price_agreement(customer_id,product_id,minimum_quantity,valid_from,valid_to);
CREATE INDEX ix_price_agreement_product ON price_agreement(product_id);
