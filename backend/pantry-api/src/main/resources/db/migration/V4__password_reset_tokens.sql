CREATE TABLE password_reset_tokens
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(100) NOT NULL UNIQUE,
    expires_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);