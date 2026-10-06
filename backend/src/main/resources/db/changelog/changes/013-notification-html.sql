--liquibase formatted sql

--changeset booking-platform:013-notification-html
-- Branded HTML version of an email (the plain-text body stays as the fallback),
-- plus who it appears to come from and where replies go.
ALTER TABLE notification
    ADD COLUMN html_body    TEXT,
    ADD COLUMN sender_name  VARCHAR(160),
    ADD COLUMN reply_to     VARCHAR(320);
