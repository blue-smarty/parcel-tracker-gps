CREATE TABLE smtp_settings (
    id varchar(255) PRIMARY KEY,
    host varchar(253) NOT NULL,
    port integer NOT NULL,
    username varchar(255) NOT NULL,
    from_address varchar(255) NOT NULL,
    security varchar(255) NOT NULL,
    authentication boolean NOT NULL
);
