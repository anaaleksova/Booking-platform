-- ---------------------------------------------------------------------------
-- V2: the bookable catalogue — services, staff and resources, plus the
--     many-to-many links that decide who/what can deliver a given service.
-- ---------------------------------------------------------------------------

CREATE TABLE services (
    id                     BIGSERIAL PRIMARY KEY,
    business_id            BIGINT        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name                   VARCHAR(160)  NOT NULL,
    description            TEXT,
    duration_minutes       INTEGER       NOT NULL,
    price                  NUMERIC(12, 2),
    currency               VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    buffer_before_minutes  INTEGER       NOT NULL DEFAULT 0,
    buffer_after_minutes   INTEGER       NOT NULL DEFAULT 0,
    -- Generic requirement flags: a haircut needs staff, a meeting room needs a
    -- resource, a reformer class needs both. No business-type branching.
    requires_staff         BOOLEAN       NOT NULL DEFAULT TRUE,
    requires_resource      BOOLEAN       NOT NULL DEFAULT FALSE,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_services_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_services_buffers CHECK (buffer_before_minutes >= 0 AND buffer_after_minutes >= 0)
);

CREATE INDEX idx_services_business ON services (business_id);
CREATE INDEX idx_services_business_active ON services (business_id, active);

CREATE TABLE staff (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    -- Nullable on purpose: a staff member does not need a login account.
    user_id     BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    name        VARCHAR(160) NOT NULL,
    email       VARCHAR(320),
    phone       VARCHAR(40),
    photo_url   VARCHAR(1024),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_business ON staff (business_id);
CREATE INDEX idx_staff_business_active ON staff (business_id, active);
CREATE UNIQUE INDEX uq_staff_user_per_business ON staff (business_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE staff_services (
    id         BIGSERIAL PRIMARY KEY,
    staff_id   BIGINT NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    CONSTRAINT uq_staff_services UNIQUE (staff_id, service_id)
);

CREATE INDEX idx_staff_services_service ON staff_services (service_id);

CREATE TABLE resources (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_resources_business ON resources (business_id);
CREATE INDEX idx_resources_business_active ON resources (business_id, active);

CREATE TABLE resource_services (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    service_id  BIGINT NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    CONSTRAINT uq_resource_services UNIQUE (resource_id, service_id)
);

CREATE INDEX idx_resource_services_service ON resource_services (service_id);
