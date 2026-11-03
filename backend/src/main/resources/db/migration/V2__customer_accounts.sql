CREATE TABLE customer_accounts (
    email varchar(255) PRIMARY KEY,
    display_name varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);
