--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 026: events on the booking page — a live band on Friday, a wine tasting —
-- shown for a while beforehand, optionally bookable through a service.
-- ---------------------------------------------------------------------------

--changeset booking-platform:026-events
CREATE TABLE business_event (
    id              BIGSERIAL     PRIMARY KEY,
    business_id     BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    title           VARCHAR(160)  NOT NULL,
    description     VARCHAR(4000),
    -- Who is on: a band, a DJ, a guest chef.
    performer       VARCHAR(160),
    image_media_id  UUID          REFERENCES media (id) ON DELETE SET NULL,
    -- Wall-clock in the business timezone. An end before the start runs past midnight.
    event_date      DATE          NOT NULL,
    start_time      TIME          NOT NULL,
    end_time        TIME,
    -- The days the event is on the booking page (inclusive, business timezone).
    show_from       DATE          NOT NULL,
    show_until      DATE          NOT NULL,
    -- What customers book for it ("Table reservation"); none = an announcement only.
    service_id      BIGINT        REFERENCES service (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_business_event_window CHECK (show_from <= show_until AND show_until <= event_date)
);

CREATE INDEX idx_business_event_showing ON business_event (business_id, show_until, show_from);
