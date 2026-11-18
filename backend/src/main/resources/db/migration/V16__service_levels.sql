CREATE TABLE service_level_settings (
    id varchar(255) PRIMARY KEY,
    general_minutes integer NOT NULL,
    express_minutes integer NOT NULL,
    target_start varchar(255) NOT NULL
);

ALTER TABLE parcels
    ADD COLUMN target_minutes integer,
    ADD COLUMN target_start varchar(255),
    ADD COLUMN target_deadline timestamptz;
