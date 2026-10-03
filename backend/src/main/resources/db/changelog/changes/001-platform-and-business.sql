--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 001: platform users, tenants (businesses), membership, settings and
--      opening hours.
-- ---------------------------------------------------------------------------

--changeset booking-platform:001-extensions
-- Required for the range EXCLUDE constraint on reservation_resource
-- (bigint WITH = plus tstzrange WITH && inside one GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

--changeset booking-platform:001-app-user
-- "user" is a reserved word in PostgreSQL, hence app_user.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(160) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

--changeset booking-platform:001-business
CREATE TABLE business (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    -- Public URL segment of the customer-facing pages.
    slug        VARCHAR(80)  NOT NULL,
    description TEXT,
    email       VARCHAR(320),
    phone       VARCHAR(40),
    address     VARCHAR(500),
    logo_url    VARCHAR(1024),
    -- ISO-4217. One currency per business; every price and reservation of the
    -- business is expressed in it.
    currency    VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_slug UNIQUE (slug),
    CONSTRAINT ck_business_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_business_currency CHECK (currency ~ '^[A-Z]{3}$')
);

--changeset booking-platform:001-business-user
-- Membership of a login account in a business, with the role held there.
CREATE TABLE business_user (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_user UNIQUE (user_id, business_id),
    CONSTRAINT ck_business_user_role CHECK (role IN ('BUSINESS_OWNER', 'STAFF'))
);

CREATE INDEX idx_business_user_business ON business_user (business_id);

--changeset booking-platform:001-business-settings
-- Booking rules and notification toggles. Exactly one row per business.
CREATE TABLE business_settings (
    id                              BIGSERIAL PRIMARY KEY,
    business_id                     BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    minimum_booking_notice_minutes  INTEGER     NOT NULL DEFAULT 60,
    maximum_booking_advance_days    INTEGER     NOT NULL DEFAULT 60,
    slot_interval_minutes           INTEGER     NOT NULL DEFAULT 30,
    allow_customer_cancellation     BOOLEAN     NOT NULL DEFAULT TRUE,
    cancellation_deadline_hours     INTEGER     NOT NULL DEFAULT 12,
    email_notifications_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    reminder_notifications_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    reminder_lead_hours             INTEGER     NOT NULL DEFAULT 24,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_settings_business UNIQUE (business_id),
    CONSTRAINT ck_settings_notice CHECK (minimum_booking_notice_minutes >= 0),
    CONSTRAINT ck_settings_advance CHECK (maximum_booking_advance_days > 0),
    CONSTRAINT ck_settings_slot CHECK (slot_interval_minutes BETWEEN 5 AND 480),
    CONSTRAINT ck_settings_deadline CHECK (cancellation_deadline_hours >= 0),
    CONSTRAINT ck_settings_reminder_lead CHECK (reminder_lead_hours > 0)
);

--changeset booking-platform:001-business-hours
-- Opening hours: wall-clock times in the *business* timezone, deliberately not
-- instants, so "Monday 09:00" stays 09:00 across DST changes. Several rows on
-- one day express a break (09:00-13:00 + 14:00-18:00). No rows = closed.
CREATE TABLE business_hours (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    day_of_week SMALLINT    NOT NULL,          -- ISO-8601: 1 = Monday .. 7 = Sunday
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hours_order CHECK (start_time < end_time)
);

CREATE INDEX idx_business_hours_business_day ON business_hours (business_id, day_of_week);
