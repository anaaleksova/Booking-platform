--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 003: customers, discounts, reservations and the resources assigned to them.
-- ---------------------------------------------------------------------------

--changeset booking-platform:003-customer
-- Tenant-scoped: the same person at two businesses is two unrelated rows.
CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(80)  NOT NULL DEFAULT '',
    email       VARCHAR(320),
    phone       VARCHAR(40),
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

CREATE INDEX idx_customer_business ON customer (business_id);
CREATE INDEX idx_customer_business_name ON customer (business_id, lower(last_name), lower(first_name));
-- What lets a returning customer be recognised instead of duplicated.
CREATE UNIQUE INDEX uq_customer_business_email ON customer (business_id, lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_customer_business_phone ON customer (business_id, phone) WHERE phone IS NOT NULL;

--changeset booking-platform:003-discount
CREATE TABLE discount (
    id                      BIGSERIAL PRIMARY KEY,
    business_id             BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name                    VARCHAR(160)  NOT NULL,
    code                    VARCHAR(40)   NOT NULL,
    discount_type           VARCHAR(16)   NOT NULL,
    discount_value          NUMERIC(12, 2) NOT NULL,
    -- Inclusive calendar dates in the business timezone; NULL = open-ended.
    starts_on               DATE,
    ends_on                 DATE,
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    max_usage               INTEGER,
    -- Incremented with a guarded UPDATE, so the limit holds under concurrency.
    usage_count             INTEGER       NOT NULL DEFAULT 0,
    min_booking_value       NUMERIC(12, 2),
    -- FALSE: only the services listed in discount_service are eligible.
    applies_to_all_services BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_discount_value CHECK (discount_value > 0),
    CONSTRAINT ck_discount_percentage CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100),
    CONSTRAINT ck_discount_period CHECK (starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on),
    CONSTRAINT ck_discount_max_usage CHECK (max_usage IS NULL OR max_usage > 0),
    CONSTRAINT ck_discount_usage CHECK (usage_count >= 0 AND (max_usage IS NULL OR usage_count <= max_usage)),
    CONSTRAINT ck_discount_min_value CHECK (min_booking_value IS NULL OR min_booking_value >= 0)
);

CREATE INDEX idx_discount_business ON discount (business_id);
-- Codes are matched case-insensitively: "summer20" finds "SUMMER20".
CREATE UNIQUE INDEX uq_discount_business_code ON discount (business_id, upper(code));

CREATE TABLE discount_service (
    id          BIGSERIAL PRIMARY KEY,
    discount_id BIGINT NOT NULL REFERENCES discount (id) ON DELETE CASCADE,
    service_id  BIGINT NOT NULL REFERENCES service (id) ON DELETE CASCADE,
    CONSTRAINT uq_discount_service UNIQUE (discount_id, service_id)
);

CREATE INDEX idx_discount_service_service ON discount_service (service_id);

--changeset booking-platform:003-reservation
CREATE TABLE reservation (
    id                      BIGSERIAL PRIMARY KEY,
    business_id             BIGINT         NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    customer_id             BIGINT         NOT NULL REFERENCES customer (id) ON DELETE RESTRICT,
    service_id              BIGINT         NOT NULL REFERENCES service (id) ON DELETE RESTRICT,

    -- What the customer booked and the calendar shows.
    start_time              TIMESTAMPTZ    NOT NULL,
    end_time                TIMESTAMPTZ    NOT NULL,
    -- The occupied window including the service buffers; conflict detection
    -- uses this, so later buffer edits cannot make history overlap.
    block_start_time        TIMESTAMPTZ    NOT NULL,
    block_end_time          TIMESTAMPTZ    NOT NULL,

    number_of_participants  INTEGER        NOT NULL,
    status                  VARCHAR(16)    NOT NULL,

    -- Price snapshot: later edits to the service or discount never restate history.
    original_price          NUMERIC(12, 2) NOT NULL,
    discount_amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    final_price             NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    discount_id             BIGINT         REFERENCES discount (id) ON DELETE SET NULL,
    discount_code           VARCHAR(40),

    customer_notes          TEXT,
    internal_notes          TEXT,
    source                  VARCHAR(16)    NOT NULL DEFAULT 'PUBLIC',
    -- Unguessable handle for the customer's own reservation page.
    public_reference        UUID           NOT NULL DEFAULT gen_random_uuid(),
    created_by_user_id      BIGINT         REFERENCES app_user (id) ON DELETE SET NULL,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     VARCHAR(255),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_reservation_public_reference UNIQUE (public_reference),
    CONSTRAINT ck_reservation_window CHECK (start_time < end_time),
    CONSTRAINT ck_reservation_block_window CHECK (block_start_time <= start_time AND block_end_time >= end_time),
    CONSTRAINT ck_reservation_participants CHECK (number_of_participants >= 1),
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_reservation_source CHECK (source IN ('PUBLIC', 'ADMIN')),
    CONSTRAINT ck_reservation_prices CHECK (original_price >= 0 AND discount_amount >= 0
                                            AND discount_amount <= original_price
                                            AND final_price = original_price - discount_amount)
);

CREATE INDEX idx_reservation_business_start ON reservation (business_id, start_time);
CREATE INDEX idx_reservation_business_end ON reservation (business_id, end_time);
CREATE INDEX idx_reservation_business_status ON reservation (business_id, status);
CREATE INDEX idx_reservation_customer ON reservation (customer_id, start_time DESC);
CREATE INDEX idx_reservation_service ON reservation (service_id);
CREATE INDEX idx_reservation_discount ON reservation (discount_id) WHERE discount_id IS NOT NULL;

--changeset booking-platform:003-reservation-resource
-- Which concrete resources a reservation holds. The block window and an
-- "occupying" flag are copied from the reservation because an exclusion
-- constraint can only see its own row.
CREATE TABLE reservation_resource (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT      NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    reservation_id   BIGINT      NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    resource_id      BIGINT      NOT NULL REFERENCES resource (id) ON DELETE RESTRICT,
    -- The requirement this resource satisfies; kept for display only.
    requirement_id   BIGINT      REFERENCES service_resource_requirement (id) ON DELETE SET NULL,
    block_start_time TIMESTAMPTZ NOT NULL,
    block_end_time   TIMESTAMPTZ NOT NULL,
    -- FALSE once the reservation is cancelled: the resource is released but
    -- the history of what was assigned is kept.
    occupying        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_reservation_resource UNIQUE (reservation_id, resource_id),
    CONSTRAINT ck_reservation_resource_window CHECK (block_start_time < block_end_time)
);

CREATE INDEX idx_reservation_resource_reservation ON reservation_resource (reservation_id);
CREATE INDEX idx_reservation_resource_resource ON reservation_resource (resource_id, block_start_time);
CREATE INDEX idx_reservation_resource_business_window
    ON reservation_resource (business_id, block_start_time, block_end_time) WHERE occupying;

-- ---------------------------------------------------------------------------
-- Double-booking prevention, enforced by the database.
--
-- The application checks availability inside the reservation transaction, but
-- two transactions can pass that check at the same moment. This constraint is
-- the guarantee that survives concurrency: the second INSERT fails and the
-- service layer turns it into 409 RESOURCE_NOT_AVAILABLE.
-- ---------------------------------------------------------------------------
ALTER TABLE reservation_resource
    ADD CONSTRAINT excl_reservation_resource_overlap
        EXCLUDE USING gist (
            resource_id WITH =,
            tstzrange(block_start_time, block_end_time, '[)') WITH &&
        )
        WHERE (occupying);
