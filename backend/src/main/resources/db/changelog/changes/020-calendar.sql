--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 020: calendars. The owner subscribes to a private feed of the business's
-- reservations (Google, Apple, Outlook); the secret in its URL is the only
-- credential, so it can be replaced. Customer emails carry the event as an
-- .ics attachment, stored with the queued message like its body.
-- ---------------------------------------------------------------------------

--changeset booking-platform:020-calendar
ALTER TABLE business_settings ADD COLUMN calendar_feed_token VARCHAR(64);
CREATE UNIQUE INDEX uq_business_settings_calendar_feed_token ON business_settings (calendar_feed_token)
    WHERE calendar_feed_token IS NOT NULL;
ALTER TABLE notification ADD COLUMN calendar_ics TEXT;
--rollback ALTER TABLE notification DROP COLUMN calendar_ics;
--rollback DROP INDEX uq_business_settings_calendar_feed_token;
--rollback ALTER TABLE business_settings DROP COLUMN calendar_feed_token;
