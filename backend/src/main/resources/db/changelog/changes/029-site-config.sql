--liquibase formatted sql

--changeset booking-platform:029-site-config
-- The website builder: each business's homepage as a JSON document, as the
-- owner's draft and as the published copy customers see. A business without
-- a row shows the default page built from its profile, which is the page it
-- had before the builder, so no existing data needs converting.
CREATE TABLE site_config (
    business_id   BIGINT PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    draft         JSONB       NOT NULL,
    published     JSONB       NOT NULL,
    draft_version INT         NOT NULL DEFAULT 0,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
