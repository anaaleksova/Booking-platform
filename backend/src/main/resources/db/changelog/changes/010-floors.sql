--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Several floor plans per resource type: a co-working space with desks on
-- three floors draws one plan per floor. Plans get a name and an order, and a
-- resource stands on at most one of them.
-- ---------------------------------------------------------------------------

--changeset booking-platform:010-floors
ALTER TABLE floor_plan
    ADD COLUMN name VARCHAR(60) NOT NULL DEFAULT 'Main floor',
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    DROP CONSTRAINT uq_floor_plan_type;
ALTER TABLE floor_plan ALTER COLUMN name DROP DEFAULT;
CREATE INDEX idx_floor_plan_type ON floor_plan (resource_type_id, position);

-- A desk is on one floor only (a resource belongs to one type, hence one set of plans).
ALTER TABLE floor_plan_item DROP CONSTRAINT uq_floor_plan_item_resource;
CREATE UNIQUE INDEX uq_floor_plan_item_resource ON floor_plan_item (resource_id);
