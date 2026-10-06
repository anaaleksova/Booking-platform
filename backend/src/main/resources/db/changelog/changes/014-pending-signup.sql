--liquibase formatted sql

--changeset booking-platform:014-pending-signup
-- A customer sign-up waiting for its emailed code. The account (app_user) is
-- only created once the code proves the email exists and belongs to them.
CREATE TABLE pending_signup (
    email         VARCHAR(320) PRIMARY KEY,
    full_name     VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    expires_at    TIMESTAMPTZ  NOT NULL,
    sent_at       TIMESTAMPTZ  NOT NULL
);
