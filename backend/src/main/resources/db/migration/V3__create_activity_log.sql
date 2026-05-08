CREATE TABLE activity_log (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    resource_id   VARCHAR(64),
    resource_type VARCHAR(20),
    action        VARCHAR(50)  NOT NULL,         -- CREATE, DELETE, START, STOP, PULL, etc.
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);