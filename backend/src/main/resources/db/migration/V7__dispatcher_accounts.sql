CREATE TABLE dispatcher_accounts (
    username varchar(40) PRIMARY KEY,
    password_hash varchar(255) NOT NULL
);
