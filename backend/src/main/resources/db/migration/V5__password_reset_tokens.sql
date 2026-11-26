-- "Forgot password" links. Like refresh tokens, only the SHA-256 hash of the token is stored.
-- A token is valid for 30 minutes and can be used once (used_at is set when it is used or
-- when a newer reset makes it obsolete).
CREATE TABLE password_reset_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_password_reset_tokens PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL CONSTRAINT df_password_reset_tokens_created_at DEFAULT now(),
    expires_at  TIMESTAMPTZ   NOT NULL,
    used_at     TIMESTAMPTZ   NULL,
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
