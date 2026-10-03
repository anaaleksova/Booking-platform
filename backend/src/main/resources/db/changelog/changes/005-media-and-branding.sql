--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 005: uploaded images and the customisable booking page.
--
-- Images live in PostgreSQL rather than on a disk: one backup covers them,
-- every container sees them, and tenant ownership is a foreign key. They are
-- small (logos, covers, service photos) and capped at 5 MB each.
-- ---------------------------------------------------------------------------

--changeset booking-platform:005-media
CREATE TABLE media (
    id           UUID         PRIMARY KEY,
    business_id  BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    content_type VARCHAR(32)  NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')),
    CONSTRAINT ck_media_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE INDEX idx_media_business ON media (business_id);

--changeset booking-platform:005-business-branding
ALTER TABLE business DROP COLUMN logo_url;
ALTER TABLE business
    -- One line under the name on the booking page, e.g. "Classic cuts, no waiting".
    ADD COLUMN tagline        VARCHAR(200),
    ADD COLUMN accent_color   VARCHAR(7)   NOT NULL DEFAULT '#2f5bea',
    ADD COLUMN logo_media_id  UUID         REFERENCES media (id) ON DELETE SET NULL,
    ADD COLUMN cover_media_id UUID         REFERENCES media (id) ON DELETE SET NULL,
    ADD COLUMN instagram_url  VARCHAR(500),
    ADD COLUMN facebook_url   VARCHAR(500),
    ADD COLUMN website_url    VARCHAR(500),
    ADD COLUMN show_map       BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_business_accent CHECK (accent_color ~ '^#[0-9a-fA-F]{6}$');

--changeset booking-platform:005-catalogue-images
ALTER TABLE service
    -- Groups the price list on the booking page ("Massages", "Face care").
    ADD COLUMN category       VARCHAR(80),
    ADD COLUMN image_media_id UUID REFERENCES media (id) ON DELETE SET NULL;

-- Employees are resources, so a resource's image is also the employee's photo.
ALTER TABLE resource ADD COLUMN image_media_id UUID REFERENCES media (id) ON DELETE SET NULL;
ALTER TABLE employee DROP COLUMN photo_url;
