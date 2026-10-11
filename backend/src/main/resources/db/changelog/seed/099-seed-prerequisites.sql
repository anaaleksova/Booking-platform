--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Lets the demo seed run on a fresh database. Seed 102 was written before
-- floor plans had names (changeset 010) and inserts none, so on a fresh
-- database it hit the NOT NULL. A temporary default fills the gap; seed 106
-- names the plan, and 108 removes the default again.
-- ---------------------------------------------------------------------------

--changeset booking-platform:099-seed-prerequisites context:dev
ALTER TABLE floor_plan ALTER COLUMN name SET DEFAULT 'Main floor';
