CREATE TABLE IF NOT EXISTS nl2db.api_keys (
    id UUID NOT NULL PRIMARY KEY,
    organization_id UUID NOT NULL,
    creator_user_id UUID NOT NULL,
    key_name VARCHAR(255) NOT NULL,
    public_prefix VARCHAR(16) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    CONSTRAINT uk_api_keys_public_prefix UNIQUE (public_prefix),
    CONSTRAINT fk_api_keys_organization FOREIGN KEY (organization_id) REFERENCES nl2db.organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_api_keys_creator FOREIGN KEY (creator_user_id) REFERENCES nl2db.users (id) ON DELETE CASCADE
);