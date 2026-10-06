--liquibase formatted sql

--changeset booking-platform:012-dark-booking-page
-- Lets a business show its customer-facing pages on a black background.
ALTER TABLE business ADD COLUMN dark_theme BOOLEAN NOT NULL DEFAULT FALSE;
