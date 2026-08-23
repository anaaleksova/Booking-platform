-- ---------------------------------------------------------------------------
-- V4: customers and bookings.
-- ---------------------------------------------------------------------------

-- Customers are tenant-scoped: the same person booking at two businesses is
-- two rows. Businesses must never see each other's customer lists.
CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name        VARCHAR(160) NOT NULL,
    phone       VARCHAR(40),
    email       VARCHAR(320),
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_customers_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

CREATE INDEX idx_customers_business ON customers (business_id);
CREATE INDEX idx_customers_business_name ON customers (business_id, lower(name));
CREATE UNIQUE INDEX uq_customers_business_email ON customers (business_id, lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_customers_business_phone ON customers (business_id, phone) WHERE phone IS NOT NULL;

CREATE TABLE bookings (
    id                      BIGSERIAL PRIMARY KEY,
    business_id             BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    service_id              BIGINT      NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    staff_id                BIGINT      REFERENCES staff (id) ON DELETE RESTRICT,
    resource_id             BIGINT      REFERENCES resources (id) ON DELETE RESTRICT,
    customer_id             BIGINT      NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,

    -- The appointment the customer sees.
    start_date_time         TIMESTAMPTZ NOT NULL,
    end_date_time           TIMESTAMPTZ NOT NULL,
    -- The occupied window including service buffers. Conflict detection uses
    -- this, never the visible window, so buffers are enforced by the database.
    block_start_date_time   TIMESTAMPTZ NOT NULL,
    block_end_date_time     TIMESTAMPTZ NOT NULL,

    status                  VARCHAR(16) NOT NULL,
    customer_notes          TEXT,
    internal_notes          TEXT,

    -- Price snapshot: later edits to the service must not rewrite history.
    price                   NUMERIC(12, 2),
    currency                VARCHAR(3),

    source                  VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    -- Unguessable handle used by the customer-facing confirmation/cancel link.
    public_reference        UUID        NOT NULL DEFAULT gen_random_uuid(),
    created_by_user_id      BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_bookings_public_reference UNIQUE (public_reference),
    CONSTRAINT ck_bookings_window CHECK (start_date_time < end_date_time),
    CONSTRAINT ck_bookings_block_window CHECK (block_start_date_time <= start_date_time
                                           AND block_end_date_time >= end_date_time),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT ck_bookings_source CHECK (source IN ('PUBLIC', 'DASHBOARD'))
);

CREATE INDEX idx_bookings_business_start ON bookings (business_id, start_date_time);
CREATE INDEX idx_bookings_business_status ON bookings (business_id, status);
CREATE INDEX idx_bookings_staff_window ON bookings (staff_id, start_date_time) WHERE staff_id IS NOT NULL;
CREATE INDEX idx_bookings_resource_window ON bookings (resource_id, start_date_time) WHERE resource_id IS NOT NULL;
CREATE INDEX idx_bookings_customer ON bookings (customer_id, start_date_time DESC);
CREATE INDEX idx_bookings_service ON bookings (service_id);

-- ---------------------------------------------------------------------------
-- Double-booking prevention, enforced by the database.
--
-- The application also re-checks conflicts inside the booking transaction, but
-- two transactions can pass that check simultaneously. These GiST exclusion
-- constraints are the guarantee that survives concurrency: the second INSERT
-- fails, and the service layer translates it into 409 BOOKING_NOT_AVAILABLE.
--
-- Only statuses that actually occupy time participate. CANCELLED and NO_SHOW
-- release the slot.
-- ---------------------------------------------------------------------------
ALTER TABLE bookings
    ADD CONSTRAINT excl_bookings_staff_overlap
        EXCLUDE USING gist (
            staff_id WITH =,
            tstzrange(block_start_date_time, block_end_date_time, '[)') WITH &&
        )
        WHERE (staff_id IS NOT NULL AND status IN ('PENDING', 'CONFIRMED', 'COMPLETED'));

ALTER TABLE bookings
    ADD CONSTRAINT excl_bookings_resource_overlap
        EXCLUDE USING gist (
            resource_id WITH =,
            tstzrange(block_start_date_time, block_end_date_time, '[)') WITH &&
        )
        WHERE (resource_id IS NOT NULL AND status IN ('PENDING', 'CONFIRMED', 'COMPLETED'));
