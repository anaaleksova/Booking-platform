--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Shared seating and minimum group size.
--
-- A shared resource (a long table, a co-working bench) can be booked by
-- several groups at once until its seats are used up: a party of two at an
-- eight-seat table leaves six seats for others. Such bookings record how many
-- seats they hold; whole-resource bookings keep seats NULL and stay protected
-- by the exclusion constraint, which now ignores seat bookings. A trigger
-- guards the seat count instead, under a per-resource lock so concurrent
-- bookings cannot oversell it.
--
-- min_party_size lets a business keep a big private table for big groups.
-- ---------------------------------------------------------------------------

--changeset booking-platform:009-shared-seating
ALTER TABLE resource
    ADD COLUMN shared_seating BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN min_party_size INTEGER,
    ADD CONSTRAINT ck_resource_min_party CHECK (min_party_size IS NULL OR min_party_size >= 1),
    ADD CONSTRAINT ck_resource_shared_capacity CHECK (NOT shared_seating OR capacity IS NOT NULL);

ALTER TABLE reservation_resource
    ADD COLUMN seats INTEGER,
    ADD CONSTRAINT ck_reservation_resource_seats CHECK (seats IS NULL OR seats >= 1);

ALTER TABLE reservation_resource DROP CONSTRAINT excl_reservation_resource_overlap;
ALTER TABLE reservation_resource
    ADD CONSTRAINT excl_reservation_resource_overlap
        EXCLUDE USING gist (
            resource_id WITH =,
            tstzrange(block_start_time, block_end_time, '[)') WITH &&
        )
        WHERE (occupying AND seats IS NULL);

--changeset booking-platform:009-shared-seating-guard splitStatements:false
CREATE FUNCTION reservation_resource_seats_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    seat_capacity INTEGER;
    peak          BIGINT;
BEGIN
    -- One booking at a time per resource; held until the transaction ends.
    PERFORM pg_advisory_xact_lock(7311, NEW.resource_id::INTEGER);

    IF NEW.seats IS NULL THEN
        -- A whole-resource booking may not overlap seat bookings.
        IF EXISTS (SELECT 1
                   FROM reservation_resource o
                   WHERE o.resource_id = NEW.resource_id
                     AND o.occupying
                     AND o.seats IS NOT NULL
                     AND o.id IS DISTINCT FROM NEW.id
                     AND o.block_start_time < NEW.block_end_time
                     AND o.block_end_time > NEW.block_start_time) THEN
            RAISE EXCEPTION 'reservation_resource_seats: resource % is partly booked', NEW.resource_id
                USING ERRCODE = 'exclusion_violation';
        END IF;
        RETURN NEW;
    END IF;

    SELECT capacity INTO seat_capacity FROM resource WHERE id = NEW.resource_id;
    IF seat_capacity IS NULL THEN
        RETURN NEW;                                   -- not limited by seats
    END IF;

    -- The busiest moment inside the new window: occupancy only rises when a
    -- booking starts, so checking the window start and every start inside it
    -- is enough. A whole-resource booking counts as every seat.
    SELECT COALESCE(MAX(load), 0) INTO peak
    FROM (SELECT SUM(COALESCE(o.seats, seat_capacity)) AS load
          FROM (SELECT NEW.block_start_time AS t
                UNION
                SELECT s.block_start_time
                FROM reservation_resource s
                WHERE s.resource_id = NEW.resource_id
                  AND s.occupying
                  AND s.id IS DISTINCT FROM NEW.id
                  AND s.block_start_time > NEW.block_start_time
                  AND s.block_start_time < NEW.block_end_time) points
          JOIN reservation_resource o
            ON o.resource_id = NEW.resource_id
           AND o.occupying
           AND o.id IS DISTINCT FROM NEW.id
           AND o.block_start_time <= points.t
           AND o.block_end_time > points.t
          GROUP BY points.t) loads;

    IF peak + NEW.seats > seat_capacity THEN
        RAISE EXCEPTION 'reservation_resource_seats: not enough seats left on resource %', NEW.resource_id
            USING ERRCODE = 'exclusion_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_reservation_resource_seats
    BEFORE INSERT OR UPDATE ON reservation_resource
    FOR EACH ROW
    WHEN (NEW.occupying)
EXECUTE FUNCTION reservation_resource_seats_guard();
