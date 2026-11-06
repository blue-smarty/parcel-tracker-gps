CREATE TABLE driver_actions (
    id varchar(36) PRIMARY KEY,
    parcel_id varchar(255) NOT NULL,
    courier_id varchar(255) NOT NULL,
    type varchar(32) NOT NULL,
    detail varchar(255),
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX driver_actions_parcel_type_idx ON driver_actions (parcel_id, type, created_at DESC);
