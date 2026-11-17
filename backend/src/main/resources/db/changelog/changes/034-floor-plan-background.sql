--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- A floor can keep a picture of the real place (a photo or drawing of its
-- floor plan), shown faintly behind the map in the editor so the owner can
-- trace walls and tables over it. Customers never see it.
-- ---------------------------------------------------------------------------

--changeset booking-platform:034-floor-plan-background
ALTER TABLE floor_plan
    ADD COLUMN background_media_id UUID REFERENCES media (id) ON DELETE SET NULL;
