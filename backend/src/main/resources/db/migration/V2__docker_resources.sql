CREATE TABLE docker_resources (
    id            BIGSERIAL    PRIMARY KEY,
    resource_id   VARCHAR(64)  NOT NULL,        -- ID del recurso en Docker
    resource_type VARCHAR(20)  NOT NULL,         -- CONTAINER | IMAGE | VOLUME | NETWORK
    resource_name VARCHAR(255),                  -- nombre legible (nginx:latest, my-network, etc.)
    owner_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(resource_id, resource_type, owner_id) -- misma imagen puede tener múltiples owners
);