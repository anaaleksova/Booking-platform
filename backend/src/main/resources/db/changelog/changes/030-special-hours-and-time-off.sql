--liquibase formatted sql

--changeset booking-platform:030-special-day
-- Opening hours for one calendar date, replacing the weekly hours on that date:
-- "Dec 24: 10:00-14:00", "open this Sunday", "closed for a private event".
-- A day with no special_day_hours rows is closed all day.
CREATE TABLE special_day (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    day         DATE         NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_special_day_business_day UNIQUE (business_id, day)
);

CREATE TABLE special_day_hours (
    special_day_id BIGINT NOT NULL REFERENCES special_day (id) ON DELETE CASCADE,
    start_time     TIME   NOT NULL,
    end_time       TIME   NOT NULL,
    CONSTRAINT ck_special_day_hours_order CHECK (start_time < end_time)
);

CREATE INDEX idx_special_day_hours_day ON special_day_hours (special_day_id);

--changeset booking-platform:030-blocked-time-author
-- Who entered the block: tells time off a staff member entered themselves
-- apart from blocks the owner made.
ALTER TABLE blocked_time
    ADD COLUMN created_by_user_id BIGINT REFERENCES app_user (id) ON DELETE SET NULL;
