--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 024: optimistic locking on reservations, and owner sign-ups confirmed by an
-- emailed code like customer sign-ups.
-- ---------------------------------------------------------------------------

--changeset booking-platform:024-reservation-version
-- Two people changing the same reservation at once (the customer cancelling
-- while the owner cancels or completes it) must not both succeed: the second
-- write now fails instead of silently repeating side effects such as giving a
-- discount use back twice.
ALTER TABLE reservation ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
--rollback ALTER TABLE reservation DROP COLUMN version;

--changeset booking-platform:024-owner-signup
-- A pending sign-up with a business name becomes an owner account plus its
-- business once the code is confirmed; without one it is a booking account.
ALTER TABLE pending_signup ADD COLUMN business_name VARCHAR(160);
ALTER TABLE pending_signup ADD COLUMN timezone VARCHAR(64);
--rollback ALTER TABLE pending_signup DROP COLUMN timezone;
--rollback ALTER TABLE pending_signup DROP COLUMN business_name;
