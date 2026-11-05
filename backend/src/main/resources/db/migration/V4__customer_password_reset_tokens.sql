CREATE TABLE password_reset_tokens (
    token_hash varchar(64) PRIMARY KEY,
    customer_email varchar(255) NOT NULL REFERENCES customer_accounts(email) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

CREATE INDEX password_reset_tokens_customer_email_idx
    ON password_reset_tokens (customer_email, created_at DESC);
