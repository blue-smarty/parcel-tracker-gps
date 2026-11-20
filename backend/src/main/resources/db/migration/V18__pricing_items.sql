ALTER TABLE pricing_settings ADD COLUMN weight_mode varchar(255) NOT NULL DEFAULT 'ACTUAL';
ALTER TABLE pricing_settings ADD COLUMN cubic_divisor double precision NOT NULL DEFAULT 5000;
ALTER TABLE parcels ADD COLUMN items jsonb;
