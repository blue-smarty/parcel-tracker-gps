ALTER TABLE customer_accounts ADD COLUMN credit_approved boolean NOT NULL DEFAULT false;

CREATE TABLE credit_applications (
    id uuid PRIMARY KEY,
    customer_email varchar(255) NOT NULL REFERENCES customer_accounts(email),
    token_hash varchar(64) UNIQUE,
    status varchar(16) NOT NULL,
    invited_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    business_name varchar(255),
    contact_name varchar(255),
    contact_email varchar(255),
    contact_phone varchar(40),
    requested_limit numeric(12,2),
    submitted_at timestamp with time zone,
    reviewed_at timestamp with time zone,
    reviewed_by varchar(255)
);

CREATE INDEX credit_applications_customer_idx ON credit_applications(customer_email);

CREATE TABLE credit_account_events (
    id uuid PRIMARY KEY,
    customer_email varchar(255) NOT NULL REFERENCES customer_accounts(email),
    action varchar(16) NOT NULL,
    actor varchar(255) NOT NULL,
    occurred_at timestamp with time zone NOT NULL
);
