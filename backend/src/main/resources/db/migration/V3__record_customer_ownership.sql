ALTER TABLE parcels ADD COLUMN customer_email varchar(200);
ALTER TABLE pickups ADD COLUMN customer_email varchar(200);

CREATE INDEX parcels_customer_email_idx ON parcels (customer_email);
CREATE INDEX pickups_customer_email_idx ON pickups (customer_email);
