--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- The phone number a customer last typed when booking, kept on their account
-- so the next booking form can be filled in for them.
-- ---------------------------------------------------------------------------

--changeset booking-platform:035-account-phone
ALTER TABLE app_user ADD COLUMN phone VARCHAR(40);

-- Accounts that booked before this: the phone of their latest online booking.
-- An online booking's customer always carries the account's email.
UPDATE app_user u
SET phone = latest.phone
FROM (SELECT DISTINCT ON (lower(c.email)) lower(c.email) AS email, c.phone
      FROM reservation r
      JOIN customer c ON c.id = r.customer_id
      WHERE r.source = 'WEB' AND c.email IS NOT NULL AND c.phone IS NOT NULL
      ORDER BY lower(c.email), r.created_at DESC) latest
WHERE lower(u.email) = latest.email;
