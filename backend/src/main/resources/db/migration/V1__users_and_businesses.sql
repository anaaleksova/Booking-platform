-- ---------------------------------------------------------------------------
-- V1: platform users, tenants (businesses), tenant membership and settings.
-- ---------------------------------------------------------------------------

-- Required for the range EXCLUDE constraints added in V4 (bigint WITH = plus
-- tstzrange WITH && inside one GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(160) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE businesses (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,
    description TEXT,
    logo_url    VARCHAR(1024),
    phone       VARCHAR(40),
    email       VARCHAR(320),
    address     VARCHAR(500),
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_businesses_slug UNIQUE (slug),
    CONSTRAINT ck_businesses_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- Membership of a user in a business, carrying the role held there.
-- A user can belong to several businesses with a different role in each.
CREATE TABLE business_users (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_users UNIQUE (user_id, business_id),
    CONSTRAINT ck_business_users_role CHECK (role IN ('BUSINESS_OWNER', 'STAFF'))
);

CREATE INDEX idx_business_users_business ON business_users (business_id);

-- Booking rules and notification toggles. Exactly one row per business.
CREATE TABLE business_settings (
    id                              BIGSERIAL PRIMARY KEY,
    business_id                     BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    minimum_booking_notice_minutes  INTEGER     NOT NULL DEFAULT 120,
    maximum_booking_advance_days    INTEGER     NOT NULL DEFAULT 60,
    slot_interval_minutes           INTEGER     NOT NULL DEFAULT 15,
    allow_customer_cancellation     BOOLEAN     NOT NULL DEFAULT TRUE,
    cancellation_deadline_hours     INTEGER     NOT NULL DEFAULT 12,
    default_appointment_duration_minutes INTEGER,
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
