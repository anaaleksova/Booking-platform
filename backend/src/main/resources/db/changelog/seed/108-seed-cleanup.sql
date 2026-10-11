--liquibase formatted sql

-- Undoes 099 once the demo seed has run.

--changeset booking-platform:108-seed-cleanup context:dev
ALTER TABLE floor_plan ALTER COLUMN name DROP DEFAULT;
