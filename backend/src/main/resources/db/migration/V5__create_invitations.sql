CREATE TABLE invitations (
    id              BIGSERIAL    PRIMARY KEY,
    token           VARCHAR(64)  UNIQUE NOT NULL,
    max_uses        INT          NOT NULL,
    uses_remaining  INT          NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    cancelled       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
