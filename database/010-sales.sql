CREATE TABLE sales_order (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    customer_name VARCHAR(160) NOT NULL,
    delivery_address VARCHAR(300) NOT NULL,
    order_date DATE NOT NULL,
    requested_date DATE NOT NULL CHECK (requested_date >= order_date),
    status VARCHAR(30) NOT NULL CHECK (status IN
      ('DRAFT','SUBMITTED','APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED','SHIPPED','CANCELLED','CLOSED_PARTIAL')),
    external_reference VARCHAR(80) NOT NULL,
    notes VARCHAR(1000) NOT NULL,
    tax_rounding VARCHAR(20) NOT NULL CHECK (tax_rounding IN ('DOWN','UP','HALF_UP')),
    net_amount NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    cancellation_reason VARCHAR(500)
);
CREATE INDEX sales_order_search_idx ON sales_order(customer_id, status, order_date);
CREATE INDEX sales_order_due_idx ON sales_order(requested_date, id) WHERE status IN
    ('APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED');

CREATE TABLE sales_order_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    order_id BIGINT NOT NULL REFERENCES sales_order(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    product_id BIGINT NOT NULL REFERENCES product(id),
    product_code VARCHAR(30) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    pack_size INTEGER NOT NULL CHECK (pack_size > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    allocated_quantity INTEGER NOT NULL DEFAULT 0 CHECK (allocated_quantity >= 0),
    shipped_quantity INTEGER NOT NULL DEFAULT 0 CHECK (shipped_quantity >= 0),
    cancelled_quantity INTEGER NOT NULL DEFAULT 0 CHECK (cancelled_quantity >= 0),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate IN (0,0.08,0.10)),
    price_reason VARCHAR(300) NOT NULL,
    UNIQUE(order_id, line_number),
    UNIQUE(order_id, product_id),
    CHECK (allocated_quantity + shipped_quantity + cancelled_quantity <= quantity),
    CHECK (quantity % pack_size = 0)
);
CREATE INDEX sales_order_line_product_idx ON sales_order_line(product_id, order_id);
