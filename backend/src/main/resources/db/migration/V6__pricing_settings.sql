CREATE TABLE pricing_settings (
    id varchar(255) PRIMARY KEY,
    base_fee double precision NOT NULL,
    per_km_rate double precision NOT NULL,
    per_kg_rate double precision NOT NULL,
    same_day_multiplier double precision NOT NULL
);
