--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 027: what platform administrators did — entering a business (which shows
-- its customers' personal data), taking businesses and accounts offline.
-- Kept even when the account or business is deleted: names are copied in.
-- ---------------------------------------------------------------------------

--changeset booking-platform:027-platform-activity
CREATE TABLE platform_activity (
    id              BIGSERIAL     PRIMARY KEY,
    actor_user_id   BIGINT        REFERENCES app_user (id) ON DELETE SET NULL,
    actor_email     VARCHAR(320)  NOT NULL,
    action          VARCHAR(40)   NOT NULL,
    -- BUSINESS or ACCOUNT, with its id and a readable name at the time.
    target_type     VARCHAR(20)   NOT NULL,
    target_id       BIGINT        NOT NULL,
    target_name     VARCHAR(320)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_platform_activity_created ON platform_activity (created_at DESC);
