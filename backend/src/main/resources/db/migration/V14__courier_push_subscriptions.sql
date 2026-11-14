CREATE TABLE courier_push_subscriptions (
    endpoint_hash varchar(64) PRIMARY KEY,
    courier_id varchar(255) NOT NULL REFERENCES courier_accounts(courier_id) ON DELETE CASCADE,
    endpoint varchar(2048) NOT NULL,
    public_key varchar(256) NOT NULL,
    auth_secret varchar(256) NOT NULL
);
CREATE INDEX courier_push_subscriptions_courier_idx ON courier_push_subscriptions(courier_id);
