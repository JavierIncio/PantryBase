ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN first_name VARCHAR (50),
    ADD COLUMN last_name VARCHAR (50),
    ADD COLUMN google_id VARCHAR (255) UNIQUE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE user_roles
(
    user_id BIGINT      NOT NULL,
    role    VARCHAR(20) NOT NULL,

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT pk_user_roles
        PRIMARY KEY (user_id, role)
);
