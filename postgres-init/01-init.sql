CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE known_places (
    id BIGSERIAL PRIMARY KEY,
    name TEXT,            -- "Home", "Starbucks North Ave", "Office"
    category TEXT,         -- "Residential", "Cafe", "Work"
    loc GEOGRAPHY(POINT, 4326),   
    created_at TIMESTAMPTZ DEFAULT NOW(),
    status TEXT NOT NULL
);

CREATE TABLE visits (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES known_places(id),
    entry_time TIMESTAMPTZ NOT NULL,
    exit_time TIMESTAMPTZ
);

CREATE TABLE location_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    device_id TEXT NOT NULL,
    loc GEOGRAPHY(POINT, 4326),
    visit_id BIGINT REFERENCES visits(id)
);

CREATE TABLE transaction_logs (
    id BIGSERIAL PRIMARY KEY,
    extern_txn_id TEXT UNIQUE NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    amount DECIMAL(10, 2),
    category TEXT,
    visit_id BIGINT REFERENCES visits(id)
);

CREATE TABLE health_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    metric_type TEXT NOT NULL,
    val DOUBLE PRECISION NOT NULL,
    unit TEXT NOT NULL,
    visit_id BIGINT REFERENCES visits(id)
);

CREATE TABLE dev_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    platform TEXT NOT NULL,
    action_type TEXT NOT NULL,
    target TEXT NOT NULL,
    metadata JSONB,
    visit_id BIGINT REFERENCES visits(id)
);
