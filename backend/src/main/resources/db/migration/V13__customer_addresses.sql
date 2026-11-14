CREATE TABLE customer_addresses (
    id varchar(255) PRIMARY KEY,
    customer_email varchar(255) NOT NULL REFERENCES customer_accounts(email) ON DELETE CASCADE,
    label varchar(80) NOT NULL,
    contact_name varchar(255) NOT NULL,
    contact_phone varchar(255) NOT NULL,
    address varchar(255) NOT NULL
);

CREATE INDEX customer_addresses_email_label_idx ON customer_addresses (customer_email, label);
