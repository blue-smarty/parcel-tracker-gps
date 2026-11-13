ALTER TABLE service_area_settings
    ADD COLUMN coverage_mode varchar(255) NOT NULL DEFAULT 'UNRESTRICTED',
    ADD COLUMN states jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN councils jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN polygons jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN polygon_couriers jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE service_area_settings
SET coverage_mode = CASE WHEN melbourne_metro_only THEN 'MELBOURNE' ELSE 'UNRESTRICTED' END;

ALTER TABLE service_area_audit
    ADD COLUMN coverage_settings jsonb NOT NULL DEFAULT '{}'::jsonb;
