--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 007: optional floor plans — a map of the place per resource type ("Table",
-- "Desk", "Court"), with each resource placed on it, so customers can see
-- which spots are free and pick one visually.
--
-- Positions and sizes are percentages of the plan, so it scales to any screen.
-- ---------------------------------------------------------------------------

--changeset booking-platform:007-floor-plans
CREATE TABLE floor_plan (
    id                  BIGSERIAL     PRIMARY KEY,
    business_id         BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    resource_type_id    BIGINT        NOT NULL REFERENCES resource_type (id) ON DELETE CASCADE,
    -- Optional drawing or photo of the room; without one a plain grid is shown.
    background_media_id UUID          REFERENCES media (id) ON DELETE SET NULL,
    -- Width divided by height of the plan.
    aspect_ratio        NUMERIC(5, 3) NOT NULL DEFAULT 1.6,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_floor_plan_type UNIQUE (resource_type_id),
    CONSTRAINT ck_floor_plan_aspect CHECK (aspect_ratio BETWEEN 0.25 AND 4)
);

CREATE INDEX idx_floor_plan_business ON floor_plan (business_id);

CREATE TABLE floor_plan_item (
    id            BIGSERIAL     PRIMARY KEY,
    floor_plan_id BIGINT        NOT NULL REFERENCES floor_plan (id) ON DELETE CASCADE,
    resource_id   BIGINT        NOT NULL REFERENCES resource (id) ON DELETE CASCADE,
    -- Centre of the spot, in % of the plan's width and height.
    x             NUMERIC(6, 3) NOT NULL,
    y             NUMERIC(6, 3) NOT NULL,
    -- Size in % of the plan's width and height.
    width         NUMERIC(6, 3) NOT NULL,
    height        NUMERIC(6, 3) NOT NULL,
    shape         VARCHAR(8)    NOT NULL DEFAULT 'ROUND',
    CONSTRAINT uq_floor_plan_item_resource UNIQUE (floor_plan_id, resource_id),
    CONSTRAINT ck_floor_plan_item_position CHECK (x BETWEEN 0 AND 100 AND y BETWEEN 0 AND 100),
    CONSTRAINT ck_floor_plan_item_size CHECK (width BETWEEN 2 AND 60 AND height BETWEEN 2 AND 60),
    CONSTRAINT ck_floor_plan_item_shape CHECK (shape IN ('ROUND', 'SQUARE', 'RECT'))
);

CREATE INDEX idx_floor_plan_item_plan ON floor_plan_item (floor_plan_id);
