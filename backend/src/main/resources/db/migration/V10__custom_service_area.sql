ALTER TABLE service_area_settings
    ADD COLUMN coverage_mode varchar(20) NOT NULL DEFAULT 'UNRESTRICTED',
    ADD COLUMN states jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN councils jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN polygons jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE service_area_settings SET coverage_mode = 'MELBOURNE' WHERE melbourne_metro_only;

ALTER TABLE service_area_audit ADD COLUMN coverage_settings jsonb;
