--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 028: what kind of business an owner signed up as, and the "Getting
-- started" checklist. A business created from a template starts with a
-- working setup; the checklist walks the owner through making it theirs.
-- ---------------------------------------------------------------------------

--changeset booking-platform:028-business-type
-- Null for businesses created before templates existed.
ALTER TABLE business ADD COLUMN business_type VARCHAR(20);
ALTER TABLE business ADD CONSTRAINT ck_business_type CHECK (business_type IN
    ('RESTAURANT', 'HAIR_SALON', 'NAILS_BEAUTY', 'SPA', 'FITNESS', 'COWORKING', 'OTHER'));

ALTER TABLE pending_signup ADD COLUMN business_type VARCHAR(20);
ALTER TABLE pending_signup ADD COLUMN currency VARCHAR(3);

--changeset booking-platform:028-business-onboarding
-- Only businesses created after this changeset get a row, so existing ones
-- never see the checklist. Each *_at is when that step was done.
CREATE TABLE business_onboarding (
    business_id          BIGINT       PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    services_checked_at  TIMESTAMPTZ,
    resources_checked_at TIMESTAMPTZ,
    hours_checked_at     TIMESTAMPTZ,
    link_shared_at       TIMESTAMPTZ,
    dismissed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
