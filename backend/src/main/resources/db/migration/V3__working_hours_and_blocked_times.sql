-- ---------------------------------------------------------------------------
-- V3: recurring availability (working hours) and one-off unavailability
--     (blocked times).
--
-- Working hours are wall-clock times in the *business* timezone, deliberately
-- not instants: "Monday 09:00" stays 09:00 across DST changes.
-- Several intervals per day are allowed, which is how lunch breaks are
-- expressed (09:00-13:00 + 14:00-18:00).
-- ---------------------------------------------------------------------------

CREATE TABLE working_hours (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    day_of_week SMALLINT    NOT NULL,          -- ISO-8601: 1 = Monday .. 7 = Sunday
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_working_hours_order CHECK (start_time < end_time)
);

CREATE INDEX idx_working_hours_business_day ON working_hours (business_id, day_of_week);

CREATE TABLE staff_working_hours (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    staff_id    BIGINT      NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    day_of_week SMALLINT    NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_staff_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_staff_working_hours_order CHECK (start_time < end_time)
);

CREATE INDEX idx_staff_working_hours_staff_day ON staff_working_hours (staff_id, day_of_week);
CREATE INDEX idx_staff_working_hours_business ON staff_working_hours (business_id);

-- A blocked time removes availability. Scope is implied by which id is set:
--   staff_id set              -> that staff member only
--   resource_id set           -> that resource only
--   both null                 -> the whole business (holiday, closure)
CREATE TABLE blocked_times (
    id              BIGSERIAL PRIMARY KEY,
    business_id     BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    staff_id        BIGINT      REFERENCES staff (id) ON DELETE CASCADE,
    resource_id     BIGINT      REFERENCES resources (id) ON DELETE CASCADE,
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time   TIMESTAMPTZ NOT NULL,
    reason          VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_blocked_times_order CHECK (start_date_time < end_date_time)
);

CREATE INDEX idx_blocked_times_business_window ON blocked_times (business_id, start_date_time, end_date_time);
CREATE INDEX idx_blocked_times_staff ON blocked_times (staff_id) WHERE staff_id IS NOT NULL;
CREATE INDEX idx_blocked_times_resource ON blocked_times (resource_id) WHERE resource_id IS NOT NULL;
