CREATE TABLE customer_cards (
    email varchar(255) PRIMARY KEY REFERENCES customer_accounts (email) ON DELETE CASCADE,
    encrypted_card text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);
