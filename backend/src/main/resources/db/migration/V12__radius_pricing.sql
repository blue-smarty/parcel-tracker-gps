ALTER TABLE pricing_settings
    ADD COLUMN pricing_mode varchar(20) NOT NULL DEFAULT 'ROUTE',
    ADD COLUMN radius_bands jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE parcels
    ADD COLUMN straight_line_distance_km double precision;
