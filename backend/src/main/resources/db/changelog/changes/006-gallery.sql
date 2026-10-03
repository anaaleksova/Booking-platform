--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 006: an optional photo gallery on the booking page.
-- ---------------------------------------------------------------------------

--changeset booking-platform:006-gallery
CREATE TABLE gallery_image (
    id          BIGSERIAL    PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    -- Deleting the image file removes it from the gallery too.
    media_id    UUID         NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    caption     VARCHAR(200),
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_gallery_image_media UNIQUE (media_id)
);

CREATE INDEX idx_gallery_image_business ON gallery_image (business_id, position);

-- Lets a business keep its photos but hide the section.
ALTER TABLE business ADD COLUMN show_gallery BOOLEAN NOT NULL DEFAULT TRUE;
