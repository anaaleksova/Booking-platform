--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 017: one reservation source per channel, so analytics can show what each
-- brings in. The customer website was called PUBLIC; it is WEB from now on.
--
-- Included after the demo seed in the master changelog: the seed writes
-- 'PUBLIC', and an applied changeset cannot be edited.
-- ---------------------------------------------------------------------------

--changeset booking-platform:017-reservation-source
ALTER TABLE reservation DROP CONSTRAINT ck_reservation_source;
UPDATE reservation SET source = 'WEB' WHERE source = 'PUBLIC';
ALTER TABLE reservation ALTER COLUMN source SET DEFAULT 'WEB';
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_source
    CHECK (source IN ('WEB', 'ADMIN', 'INSTAGRAM', 'FACEBOOK', 'API'));
