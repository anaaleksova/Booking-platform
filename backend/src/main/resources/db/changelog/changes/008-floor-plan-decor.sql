--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Floor plans are drawn, not photographed: the optional background image goes,
-- and plans gain the non-bookable parts of the room — areas (terrace, kitchen),
-- walls, counters, plants and the entrance — so customers recognise the place.
-- ---------------------------------------------------------------------------

--changeset booking-platform:008-floor-plan-decor
DELETE FROM media WHERE id IN (SELECT background_media_id FROM floor_plan WHERE background_media_id IS NOT NULL);
ALTER TABLE floor_plan DROP COLUMN background_media_id;

CREATE TABLE floor_plan_decor (
    id            BIGSERIAL PRIMARY KEY,
    floor_plan_id BIGINT       NOT NULL REFERENCES floor_plan (id) ON DELETE CASCADE,
    kind          VARCHAR(12)  NOT NULL,
    label         VARCHAR(40),
    tone          VARCHAR(8),
    -- Centre and size, in percent of the plan, like floor_plan_item.
    x             NUMERIC(6, 3) NOT NULL,
    y             NUMERIC(6, 3) NOT NULL,
    width         NUMERIC(6, 3) NOT NULL,
    height        NUMERIC(6, 3) NOT NULL,
    CONSTRAINT ck_floor_plan_decor_kind CHECK (kind IN ('ZONE', 'WALL', 'COUNTER', 'PLANT', 'ENTRANCE')),
    CONSTRAINT ck_floor_plan_decor_tone CHECK (tone IS NULL OR tone IN ('GREEN', 'PINK', 'BLUE', 'SAND')),
    CONSTRAINT ck_floor_plan_decor_position CHECK (x BETWEEN 0 AND 100 AND y BETWEEN 0 AND 100),
    CONSTRAINT ck_floor_plan_decor_size CHECK (width BETWEEN 0.5 AND 100 AND height BETWEEN 0.5 AND 100)
);
CREATE INDEX idx_floor_plan_decor_plan ON floor_plan_decor (floor_plan_id);
