--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 033: events shared to the business's Facebook Page and Instagram account.
-- One row per attempt and platform, so the owner sees what went out (with a
-- link) and what Meta refused (with Meta's reason).
-- ---------------------------------------------------------------------------

--changeset booking-platform:033-event-social-post
CREATE TABLE event_social_post (
    id             BIGSERIAL PRIMARY KEY,
    event_id       BIGINT        NOT NULL REFERENCES business_event (id) ON DELETE CASCADE,
    business_id    BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    platform       VARCHAR(16)   NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    external_id    VARCHAR(128),
    permalink      VARCHAR(1000),
    error          VARCHAR(1000),
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_event_social_post_platform CHECK (platform IN ('FACEBOOK', 'INSTAGRAM')),
    CONSTRAINT ck_event_social_post_status CHECK (status IN ('POSTED', 'FAILED'))
);

CREATE INDEX idx_event_social_post_event ON event_social_post (event_id, created_at DESC);
CREATE INDEX idx_event_social_post_business ON event_social_post (business_id);
