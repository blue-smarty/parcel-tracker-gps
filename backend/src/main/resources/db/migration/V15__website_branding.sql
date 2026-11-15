CREATE TABLE website_theme (
    id varchar(255) PRIMARY KEY,
    css text,
    font_family varchar(255)
);

CREATE TABLE branding_images (
    id varchar(36) PRIMARY KEY,
    name varchar(100) NOT NULL,
    content_type varchar(32) NOT NULL,
    data text NOT NULL
);
