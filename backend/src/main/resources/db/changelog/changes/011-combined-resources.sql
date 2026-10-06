--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Combining resources for larger groups.
--
-- With combine_resources set, a requirement's quantity is a minimum: a party
-- of eight with only four-seat tables free is given two of them, found by the
-- booking engine at booking time. No combination is ever configured or stored
-- as such — the reservation holds one reservation_resource row per resource,
-- and the existing exclusion constraint and seat guard protect each of them,
-- so two overlapping reservations still can never share a resource.
-- ---------------------------------------------------------------------------

--changeset booking-platform:011-combined-resources
ALTER TABLE service_resource_requirement
    ADD COLUMN combine_resources BOOLEAN NOT NULL DEFAULT FALSE;
