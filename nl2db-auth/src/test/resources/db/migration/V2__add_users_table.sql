CREATE TABLE users (
    id UUID NOT NULL PRIMARY KEY,
    normalized_email VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_normalized_email UNIQUE (normalized_email)
);