ALTER TABLE service_area_settings
    ADD COLUMN polygon_couriers jsonb NOT NULL DEFAULT '[]'::jsonb;
