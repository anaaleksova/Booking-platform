--liquibase formatted sql

--changeset booking-platform:015-password-reset
-- A forgotten-password code waiting to be used; one per account.
CREATE TABLE password_reset (
    user_id    BIGINT      PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INT         NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL
);
