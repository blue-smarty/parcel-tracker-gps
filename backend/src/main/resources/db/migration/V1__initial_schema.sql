CREATE TABLE parcels (
    id varchar(255) PRIMARY KEY,
    tracking_number varchar(255) NOT NULL UNIQUE,
    sender varchar(255) NOT NULL,
    recipient varchar(255) NOT NULL,
    courier_id varchar(255),
    pickup_address varchar(255),
    current_location jsonb,
    destination varchar(255),
    package_size varchar(255),
    width_cm double precision,
    length_cm double precision,
    height_cm double precision,
    weight_kg double precision,
    delivery_speed varchar(255),
    scheduled_date date,
    scheduled_window varchar(255),
    distance_km double precision,
    destination_latitude double precision,
    destination_longitude double precision,
    estimated_price double precision,
    payment_method varchar(255),
    customer_contact varchar(255),
    status varchar(255) NOT NULL,
    location_history jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    escalated_at timestamp with time zone,
    location_inactive boolean NOT NULL DEFAULT false,
    finalized_at timestamp with time zone,
    proof_of_delivery jsonb
);

CREATE INDEX status_updated_at ON parcels (status, updated_at);
CREATE INDEX status_created_at ON parcels (status, created_at);
CREATE INDEX courier_status ON parcels (courier_id, status);
CREATE INDEX parcels_courier_id_idx ON parcels (courier_id);

CREATE TABLE pickups (
    id varchar(255) PRIMARY KEY,
    street varchar(255),
    city varchar(255),
    postal_code varchar(255),
    contact_name varchar(255),
    contact_phone varchar(255),
    parcel_description text,
    weight_kg double precision NOT NULL,
    dimensions varchar(255),
    pickup_date date,
    time_window varchar(255),
    notes text,
    status varchar(255) NOT NULL,
    created_at timestamp with time zone
);

CREATE TABLE courier_accounts (
    courier_id varchar(255) PRIMARY KEY,
    password_hash varchar(255) NOT NULL
);

CREATE TABLE branding (
    id varchar(255) PRIMARY KEY,
    app_name varchar(255),
    primary_color varchar(255),
    accent_color varchar(255),
    logo_content_type varchar(255),
    logo_base64 text
);
