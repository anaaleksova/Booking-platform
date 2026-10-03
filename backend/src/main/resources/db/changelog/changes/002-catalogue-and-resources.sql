--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 002: the configurable catalogue.
--
--   resource_type  "Treatment Room", "Table", "Employee" — defined by the
--                  business, never by code
--   resource       a concrete reservable item of one type: "Room 2", "Table 5"
--   employee       a person; every employee is backed by one resource of the
--                  business's employee type, so people and things share one
--                  availability and conflict system
--   service        something customers book
--   service_resource_requirement
--                  what a service needs: "1 Treatment Room + 2 Employees"
-- ---------------------------------------------------------------------------

--changeset booking-platform:002-resource-type
CREATE TABLE resource_type (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    -- Exactly one type per business represents employees. Its resources are
    -- created and kept in sync by the employee module.
    is_employee_type BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_resource_type_business ON resource_type (business_id);
CREATE UNIQUE INDEX uq_resource_type_business_name ON resource_type (business_id, lower(name));
CREATE UNIQUE INDEX uq_resource_type_business_employee ON resource_type (business_id) WHERE is_employee_type;

--changeset booking-platform:002-resource
CREATE TABLE resource (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    resource_type_id BIGINT       NOT NULL REFERENCES resource_type (id) ON DELETE RESTRICT,
    name             VARCHAR(160) NOT NULL,
    description      TEXT,
    -- How many participants this resource can serve at once (a table's seats,
    -- a room's occupancy). NULL means participants do not limit it.
    capacity         INTEGER,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE INDEX idx_resource_business ON resource (business_id);
CREATE INDEX idx_resource_business_type ON resource (business_id, resource_type_id, active);

--changeset booking-platform:002-resource-hours
-- Optional own weekly schedule of a resource (an employee's shifts, a machine
-- only available mornings). No rows = follows the business opening hours.
CREATE TABLE resource_hours (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    resource_id BIGINT      NOT NULL REFERENCES resource (id) ON DELETE CASCADE,
    day_of_week SMALLINT    NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_resource_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_resource_hours_order CHECK (start_time < end_time)
);

CREATE INDEX idx_resource_hours_resource_day ON resource_hours (resource_id, day_of_week);
CREATE INDEX idx_resource_hours_business ON resource_hours (business_id);

--changeset booking-platform:002-employee
CREATE TABLE employee (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    -- The employee's representation in the resource system.
    resource_id BIGINT       NOT NULL REFERENCES resource (id) ON DELETE RESTRICT,
    -- Nullable: most employees never log in.
    user_id     BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    name        VARCHAR(160) NOT NULL,
    email       VARCHAR(320),
    phone       VARCHAR(40),
    photo_url   VARCHAR(1024),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_resource UNIQUE (resource_id)
);

CREATE INDEX idx_employee_business ON employee (business_id);
CREATE UNIQUE INDEX uq_employee_user_per_business ON employee (business_id, user_id) WHERE user_id IS NOT NULL;

--changeset booking-platform:002-service
CREATE TABLE service (
    id                                 BIGSERIAL PRIMARY KEY,
    business_id                        BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name                               VARCHAR(160)  NOT NULL,
    description                        TEXT,
    duration_minutes                   INTEGER       NOT NULL,
    price                              NUMERIC(12, 2) NOT NULL,
    -- FALSE: price is per reservation (a couples massage). TRUE: per person (a dinner menu).
    price_per_participant              BOOLEAN       NOT NULL DEFAULT FALSE,
    max_participants                   INTEGER       NOT NULL DEFAULT 1,
    buffer_before_minutes              INTEGER       NOT NULL DEFAULT 0,
    buffer_after_minutes               INTEGER       NOT NULL DEFAULT 0,
    allow_customer_resource_selection  BOOLEAN       NOT NULL DEFAULT FALSE,
    active                             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_service_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_service_price CHECK (price >= 0),
    CONSTRAINT ck_service_participants CHECK (max_participants >= 1),
    CONSTRAINT ck_service_buffers CHECK (buffer_before_minutes >= 0 AND buffer_after_minutes >= 0)
);

CREATE INDEX idx_service_business ON service (business_id);
CREATE INDEX idx_service_business_active ON service (business_id, active);

--changeset booking-platform:002-service-resource-requirement
CREATE TABLE service_resource_requirement (
    id                  BIGSERIAL PRIMARY KEY,
    service_id          BIGINT      NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    resource_type_id    BIGINT      NOT NULL REFERENCES resource_type (id) ON DELETE RESTRICT,
    quantity            INTEGER     NOT NULL,
    -- FALSE: assigned when one is free, but never makes a slot unavailable.
    required            BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Effective only when the service also allows customer resource selection.
    customer_can_choose BOOLEAN     NOT NULL DEFAULT FALSE,
    position            INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_requirement_quantity CHECK (quantity BETWEEN 1 AND 50),
    CONSTRAINT uq_requirement_service_type UNIQUE (service_id, resource_type_id)
);

CREATE INDEX idx_requirement_service ON service_resource_requirement (service_id);
CREATE INDEX idx_requirement_type ON service_resource_requirement (resource_type_id);

--changeset booking-platform:002-resource-service
-- Optional restriction: "Maria only performs massages". A resource with no
-- rows here can serve every service that needs its type.
CREATE TABLE resource_service (
    id          BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resource (id) ON DELETE CASCADE,
    service_id  BIGINT NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    CONSTRAINT uq_resource_service UNIQUE (resource_id, service_id)
);

CREATE INDEX idx_resource_service_service ON resource_service (service_id);

--changeset booking-platform:002-blocked-time
-- One-off unavailability: a closure (resource_id NULL) or one resource being
-- unavailable — an employee's holiday, a machine in maintenance.
CREATE TABLE blocked_time (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    resource_id BIGINT      REFERENCES resource (id) ON DELETE CASCADE,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_blocked_time_order CHECK (start_time < end_time)
);

CREATE INDEX idx_blocked_time_business_window ON blocked_time (business_id, start_time, end_time);
CREATE INDEX idx_blocked_time_resource ON blocked_time (resource_id) WHERE resource_id IS NOT NULL;
