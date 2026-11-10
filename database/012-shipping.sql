CREATE TABLE shipping_shipment (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES sales_order(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('INSTRUCTED','CONFIRMED','CANCELLED')),
    planned_date DATE NOT NULL,
    shipped_date DATE,
    carrier VARCHAR(30) NOT NULL CHECK (carrier IN ('OWN','PARCEL','FREIGHT')),
    tracking_number VARCHAR(80) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    confirmed_by VARCHAR(50),
    confirmed_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    invoice_id BIGINT,
    CHECK (status <> 'CONFIRMED' OR (shipped_date IS NOT NULL AND confirmed_at IS NOT NULL)),
    CHECK (invoice_id IS NULL OR status = 'CONFIRMED')
);
CREATE INDEX shipping_shipment_order_idx ON shipping_shipment(order_id, status);
CREATE TABLE shipping_shipment_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    shipment_id BIGINT NOT NULL REFERENCES shipping_shipment(id),
    order_line_id BIGINT NOT NULL REFERENCES sales_order_line(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    returned_quantity INTEGER NOT NULL DEFAULT 0 CHECK (returned_quantity BETWEEN 0 AND quantity),
    product_code VARCHAR(30) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate IN (0,0.08,0.10)),
    UNIQUE(shipment_id, line_number),
    UNIQUE(shipment_id, order_line_id)
);
CREATE INDEX shipping_line_order_idx ON shipping_shipment_line(order_line_id);
CREATE TABLE sales_return (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    number VARCHAR(50) NOT NULL UNIQUE,
    shipment_id BIGINT NOT NULL REFERENCES shipping_shipment(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','RECEIVED','REJECTED','CANCELLED')),
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('DAMAGED','CUSTOMER_CHANGE','MISSHIP','EXPIRED')),
    requested_date DATE NOT NULL,
    received_date DATE,
    created_by VARCHAR(50) NOT NULL,
    approved_by VARCHAR(50),
    received_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    received_at TIMESTAMP,
    notes VARCHAR(1000) NOT NULL,
    CHECK (status <> 'RECEIVED' OR (received_date IS NOT NULL AND received_at IS NOT NULL))
);
CREATE INDEX sales_return_shipment_idx ON sales_return(shipment_id, status);
CREATE TABLE sales_return_line (
    id BIGINT PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    return_id BIGINT NOT NULL REFERENCES sales_return(id),
    shipment_line_id BIGINT NOT NULL REFERENCES shipping_shipment_line(id),
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 200),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    restock BOOLEAN NOT NULL,
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(6,4) NOT NULL CHECK (tax_rate IN (0,0.08,0.10)),
    product_code VARCHAR(30) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    UNIQUE(return_id, line_number),
    UNIQUE(return_id, shipment_line_id)
);
CREATE INDEX sales_return_line_shipment_idx ON sales_return_line(shipment_line_id);
