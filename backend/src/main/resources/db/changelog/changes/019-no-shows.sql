--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 019: no-shows. A reservation the customer did not turn up for is marked
-- NO_SHOW by the business: it releases its resources (the rest of the slot
-- can go to a walk-in), earns nothing, and shows in the customer's history
-- and in analytics.
-- ---------------------------------------------------------------------------

--changeset booking-platform:019-no-show-status
ALTER TABLE reservation DROP CONSTRAINT ck_reservation_status;
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
--rollback ALTER TABLE reservation DROP CONSTRAINT ck_reservation_status;
--rollback ALTER TABLE reservation ADD CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));
