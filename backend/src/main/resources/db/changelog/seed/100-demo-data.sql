--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Development seed data — runs only with the "dev" Liquibase context.
--
-- Two tenants on purpose, configured for very different businesses from the
-- same generic model:
--
--   Serenity Studio  a spa: treatment rooms, massage chairs and employees,
--                    services needing several resources at once
--   Harbor Bistro    a restaurant: tables with seat capacity, priced per guest,
--                    guests pick their table
--
-- A second tenant also makes it obvious when a query loses its business_id
-- filter while clicking around.
--
-- Logins (password for both: password123)
--   owner@serenity.studio   -> Serenity Studio
--   owner@harbor-bistro.com -> Harbor Bistro
--
-- Reservation dates are relative to the day the seed first runs: roughly two
-- months of history plus two weeks ahead, so the dashboard, revenue and
-- analytics have real data and some slots are already taken.
-- ---------------------------------------------------------------------------

--changeset booking-platform:100-demo-data context:dev splitStatements:false
CREATE OR REPLACE FUNCTION pg_temp.seed_reservation(
    p_business      BIGINT,
    p_customer      BIGINT,
    p_service       BIGINT,
    p_start         TIMESTAMPTZ,
    p_minutes       INTEGER,
    p_buffer_after  INTEGER,
    p_participants  INTEGER,
    p_status        TEXT,
    p_price         NUMERIC,
    p_discount      NUMERIC,
    p_discount_id   BIGINT,
    p_discount_code TEXT,
    p_source        TEXT,
    p_resources     BIGINT[],
    p_requirements  BIGINT[]
) RETURNS BIGINT AS $fn$
DECLARE
    new_id    BIGINT;
    block_end TIMESTAMPTZ := p_start + make_interval(mins => p_minutes + p_buffer_after);
BEGIN
    INSERT INTO reservation (business_id, customer_id, service_id, start_time, end_time,
                             block_start_time, block_end_time, number_of_participants, status,
                             original_price, discount_amount, final_price, currency, discount_id, discount_code,
                             source, created_at, updated_at, cancelled_at)
    VALUES (p_business, p_customer, p_service, p_start, p_start + make_interval(mins => p_minutes),
            p_start, block_end, p_participants, p_status,
            p_price, p_discount, p_price - p_discount, 'EUR', p_discount_id, p_discount_code,
            p_source, LEAST(p_start - interval '3 days', now()), LEAST(p_start, now()),
            CASE WHEN p_status = 'CANCELLED' THEN LEAST(p_start - interval '1 day', now()) END)
    RETURNING id INTO new_id;

    FOR i IN 1 .. array_length(p_resources, 1) LOOP
        INSERT INTO reservation_resource (business_id, reservation_id, resource_id, requirement_id,
                                          block_start_time, block_end_time, occupying)
        VALUES (p_business, new_id, p_resources[i], p_requirements[i], p_start, block_end, p_status <> 'CANCELLED');
    END LOOP;
    RETURN new_id;
END
$fn$ LANGUAGE plpgsql;

DO $$
DECLARE
    -- BCrypt hash of 'password123'. Development-only credential.
    demo_password TEXT := '$2a$10$4HYzuABux1HvCXWygtMESuWWGsAzB.LNSJB2mhlg5vTefVmMvTYaW';
    zone          TEXT := 'Europe/Skopje';

    owner_id BIGINT;  biz BIGINT;
    t_room BIGINT;  t_chair BIGINT;  t_employee BIGINT;
    room1 BIGINT;  room2 BIGINT;  room3 BIGINT;
    chair1 BIGINT; chair2 BIGINT; chair3 BIGINT; chair4 BIGINT;
    anna BIGINT;  maria BIGINT;  elena BIGINT;  mark BIGINT;       -- employee resource ids
    s_classic BIGINT;  s_couples BIGINT;  s_facial BIGINT;
    rq_classic_room BIGINT; rq_classic_emp BIGINT; rq_classic_chair BIGINT;
    rq_couples_room BIGINT; rq_couples_emp BIGINT; rq_couples_chair BIGINT;
    rq_facial_room BIGINT;  rq_facial_emp BIGINT;
    d_welcome BIGINT;  d_couples BIGINT;
    customers BIGINT[];

    day_offset INTEGER;  the_day DATE;  dow INTEGER;  k INTEGER;
    slot TIMESTAMPTZ;  status TEXT;  cust BIGINT;  src TEXT;
    disc NUMERIC;  disc_id BIGINT;  disc_code TEXT;
    facial_emp BIGINT;

    -- restaurant
    bistro_owner BIGINT;  bistro BIGINT;  t_table BIGINT;
    tbl BIGINT[];  s_dinner BIGINT;  s_lunch BIGINT;  rq_dinner BIGINT;  rq_lunch BIGINT;
    guests BIGINT[];
BEGIN
    IF EXISTS (SELECT 1 FROM business WHERE slug = 'serenity-studio') THEN
        RETURN;
    END IF;

    -- =======================================================================
    -- Tenant 1: Serenity Studio (spa)
    -- =======================================================================
    INSERT INTO app_user (email, password_hash, full_name)
    VALUES ('owner@serenity.studio', demo_password, 'Sofia Markovska') RETURNING id INTO owner_id;

    INSERT INTO business (name, slug, description, email, phone, address, currency, timezone)
    VALUES ('Serenity Studio', 'serenity-studio',
            'A calm city-centre spa for massages and facial treatments. Book a treatment for yourself, or share a couples massage.',
            'hello@serenity.studio', '+389 2 312 4567', 'Ulica Makedonija 21, Skopje', 'EUR', zone)
    RETURNING id INTO biz;

    INSERT INTO business_user (business_id, user_id, role) VALUES (biz, owner_id, 'BUSINESS_OWNER');
    INSERT INTO business_settings (business_id, minimum_booking_notice_minutes, maximum_booking_advance_days,
                                   slot_interval_minutes, allow_customer_cancellation, cancellation_deadline_hours)
    VALUES (biz, 60, 60, 30, TRUE, 12);

    -- Mon-Wed 09-17, Thu-Fri 09-20, Sat 10-16, Sun closed.
    INSERT INTO business_hours (business_id, day_of_week, start_time, end_time)
    SELECT biz, d, '09:00', '17:00' FROM generate_series(1, 3) AS d;
    INSERT INTO business_hours (business_id, day_of_week, start_time, end_time)
    SELECT biz, d, '09:00', '20:00' FROM generate_series(4, 5) AS d;
    INSERT INTO business_hours (business_id, day_of_week, start_time, end_time) VALUES (biz, 6, '10:00', '16:00');

    -- Resource types: defined by the business, not by code.
    INSERT INTO resource_type (business_id, name, description)
    VALUES (biz, 'Treatment Room', 'Private rooms. Capacity is the number of guests treated together.')
    RETURNING id INTO t_room;
    INSERT INTO resource_type (business_id, name, description)
    VALUES (biz, 'Massage Chair', 'Heated massage tables, one guest each.') RETURNING id INTO t_chair;
    INSERT INTO resource_type (business_id, name, description, is_employee_type)
    VALUES (biz, 'Employee', 'Team members. Managed on the Employees page.', TRUE) RETURNING id INTO t_employee;

    INSERT INTO resource (business_id, resource_type_id, name, description, capacity)
    VALUES (biz, t_room, 'Room 1', 'Single treatment room', 1) RETURNING id INTO room1;
    INSERT INTO resource (business_id, resource_type_id, name, description, capacity)
    VALUES (biz, t_room, 'Room 2', 'Couples suite', 2) RETURNING id INTO room2;
    INSERT INTO resource (business_id, resource_type_id, name, description, capacity)
    VALUES (biz, t_room, 'Room 3', 'Garden room', 2) RETURNING id INTO room3;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_chair, 'Chair 1', 1) RETURNING id INTO chair1;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_chair, 'Chair 2', 1) RETURNING id INTO chair2;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_chair, 'Chair 3', 1) RETURNING id INTO chair3;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_chair, 'Chair 4', 1) RETURNING id INTO chair4;

    -- Employees: each is backed by a resource of the employee type.
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_employee, 'Anna', 1) RETURNING id INTO anna;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_employee, 'Maria', 1) RETURNING id INTO maria;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_employee, 'Elena', 1) RETURNING id INTO elena;
    INSERT INTO resource (business_id, resource_type_id, name, capacity) VALUES (biz, t_employee, 'Mark', 1) RETURNING id INTO mark;
    INSERT INTO employee (business_id, resource_id, name, email, phone) VALUES
        (biz, anna,  'Anna',  'anna@serenity.studio',  '+389 70 111 201'),
        (biz, maria, 'Maria', 'maria@serenity.studio', '+389 70 111 202'),
        (biz, elena, 'Elena', 'elena@serenity.studio', '+389 70 111 203'),
        (biz, mark,  'Mark',  'mark@serenity.studio',  '+389 70 111 204');

    -- Elena works weekdays 09-17 only, so late and Saturday slots never offer her.
    INSERT INTO resource_hours (business_id, resource_id, day_of_week, start_time, end_time)
    SELECT biz, elena, d, '09:00', '17:00' FROM generate_series(1, 5) AS d;

    -- Services and what each needs.
    INSERT INTO service (business_id, name, description, duration_minutes, price, max_participants,
                         allow_customer_resource_selection)
    VALUES (biz, 'Classic Massage', 'A full-body relaxing massage with warm oils. Choose your therapist.',
            60, 50.00, 1, TRUE) RETURNING id INTO s_classic;
    INSERT INTO service (business_id, name, description, duration_minutes, price, max_participants,
                         allow_customer_resource_selection)
    VALUES (biz, 'Couples Massage', 'Two therapists, side by side in our couples suite. Price covers both guests.',
            60, 80.00, 2, FALSE) RETURNING id INTO s_couples;
    INSERT INTO service (business_id, name, description, duration_minutes, price, max_participants,
                         buffer_after_minutes, allow_customer_resource_selection)
    VALUES (biz, 'Facial Treatment', 'Deep cleansing facial tailored to your skin type.',
            45, 35.00, 1, 15, TRUE) RETURNING id INTO s_facial;

    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_classic, t_room, 1, TRUE, FALSE, 0) RETURNING id INTO rq_classic_room;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_classic, t_employee, 1, TRUE, TRUE, 1) RETURNING id INTO rq_classic_emp;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_classic, t_chair, 1, TRUE, FALSE, 2) RETURNING id INTO rq_classic_chair;

    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_couples, t_room, 1, TRUE, FALSE, 0) RETURNING id INTO rq_couples_room;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_couples, t_employee, 2, TRUE, FALSE, 1) RETURNING id INTO rq_couples_emp;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_couples, t_chair, 2, TRUE, FALSE, 2) RETURNING id INTO rq_couples_chair;

    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_facial, t_room, 1, TRUE, FALSE, 0) RETURNING id INTO rq_facial_room;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose, position)
    VALUES (s_facial, t_employee, 1, TRUE, TRUE, 1) RETURNING id INTO rq_facial_emp;

    -- Who performs what: Elena does no couples massages, Mark does no facials.
    INSERT INTO resource_service (resource_id, service_id) VALUES
        (elena, s_classic), (elena, s_facial),
        (mark, s_classic), (mark, s_couples);

    -- Discounts: one open-ended, one expired, one limited to a service.
    INSERT INTO discount (business_id, name, code, discount_type, discount_value, active)
    VALUES (biz, 'Welcome offer', 'WELCOME10', 'PERCENTAGE', 10, TRUE) RETURNING id INTO d_welcome;
    INSERT INTO discount (business_id, name, code, discount_type, discount_value, starts_on, ends_on, active, max_usage)
    VALUES (biz, 'Summer promotion', 'SUMMER20', 'PERCENTAGE', 20,
            make_date(extract(year FROM current_date)::int, 6, 1),
            make_date(extract(year FROM current_date)::int, 6, 30), TRUE, 200);
    INSERT INTO discount (business_id, name, code, discount_type, discount_value, active, max_usage,
                          min_booking_value, applies_to_all_services)
    VALUES (biz, 'Couples treat', 'COUPLES15', 'FIXED_AMOUNT', 15, TRUE, 50, 60, FALSE) RETURNING id INTO d_couples;
    INSERT INTO discount_service (discount_id, service_id) VALUES (d_couples, s_couples);

    -- Customers.
    WITH inserted AS (
        INSERT INTO customer (business_id, first_name, last_name, email, phone) VALUES
            (biz, 'Ana',     'Petrova',    'ana.petrova@example.com',    '+389 71 200 001'),
            (biz, 'Marko',   'Ilievski',   'marko.ilievski@example.com', '+389 71 200 002'),
            (biz, 'Sara',    'Nikolova',   'sara.nikolova@example.com',  '+389 71 200 003'),
            (biz, 'David',   'Stojanov',   'david.stojanov@example.com', '+389 71 200 004'),
            (biz, 'Ivana',   'Trajkovska', 'ivana.t@example.com',        '+389 71 200 005'),
            (biz, 'Luka',    'Georgiev',   'luka.georgiev@example.com',  '+389 71 200 006'),
            (biz, 'Elif',    'Demir',      'elif.demir@example.com',     '+389 71 200 007'),
            (biz, 'Nikola',  'Jovanov',    'nikola.jovanov@example.com', '+389 71 200 008'),
            (biz, 'Mila',    'Kostova',    'mila.kostova@example.com',   '+389 71 200 009'),
            (biz, 'Tomas',   'Berg',       'tomas.berg@example.com',     '+389 71 200 010'),
            (biz, 'Jana',    'Popovska',   'jana.popovska@example.com',  '+389 71 200 011'),
            (biz, 'Filip',   'Angelov',    'filip.angelov@example.com',  '+389 71 200 012')
        RETURNING id
    )
    SELECT array_agg(id ORDER BY id) INTO customers FROM inserted;

    -- Room 3 is being serviced one morning next week.
    INSERT INTO blocked_time (business_id, resource_id, start_time, end_time, reason)
    VALUES (biz, room3, ((current_date + 5) + time '09:00') AT TIME ZONE zone,
            ((current_date + 5) + time '12:00') AT TIME ZONE zone, 'Maintenance');

    -- Reservations: a fixed, conflict-free daily pattern, thinned out pseudo-randomly.
    FOR day_offset IN -60 .. 14 LOOP
        the_day := current_date + day_offset;
        dow := extract(isodow FROM the_day);
        CONTINUE WHEN dow = 7;                                   -- closed on Sunday

        FOR k IN 1 .. 7 LOOP
            -- History is ~80% full; the future about half, so there is still room to book.
            CONTINUE WHEN day_offset < 0 AND (abs(day_offset) * 7 + k) % 5 = 0;
            CONTINUE WHEN day_offset > 0 AND (day_offset + k) % 2 = 1;
            CONTINUE WHEN k = 6 AND dow = 6;                     -- Saturday closes at 16:00
            CONTINUE WHEN k = 7 AND dow NOT IN (4, 5);           -- late slot only Thu/Fri

            status := CASE
                WHEN day_offset < 0 AND (abs(day_offset) * 3 + k) % 11 = 0 THEN 'CANCELLED'
                WHEN day_offset < 0 THEN 'COMPLETED'
                WHEN day_offset > 0 AND (day_offset + k) % 9 = 0 THEN 'CANCELLED'
                ELSE 'CONFIRMED' END;
            cust := customers[((abs(day_offset) * 7 + k * 5) % array_length(customers, 1)) + 1];
            src := CASE WHEN k % 3 = 0 THEN 'ADMIN' ELSE 'PUBLIC' END;
            facial_emp := CASE WHEN dow = 6 THEN (CASE WHEN k = 3 THEN anna ELSE maria END) ELSE elena END;
            disc := 0; disc_id := NULL; disc_code := NULL;

            IF k IN (1, 4, 7) THEN                              -- Classic Massage
                IF (abs(day_offset) + k) % 6 = 0 THEN
                    disc := 5.00; disc_id := d_welcome; disc_code := 'WELCOME10';
                END IF;
                slot := (the_day + (CASE k WHEN 1 THEN time '10:00' WHEN 4 THEN time '14:00' ELSE time '18:00' END))
                        AT TIME ZONE zone;
                PERFORM pg_temp.seed_reservation(biz, cust, s_classic, slot, 60, 0, 1, status, 50.00, disc, disc_id,
                        disc_code, src,
                        ARRAY[room1, CASE k WHEN 1 THEN anna ELSE mark END,
                              CASE k WHEN 1 THEN chair1 WHEN 4 THEN chair4 ELSE chair3 END],
                        ARRAY[rq_classic_room, rq_classic_emp, rq_classic_chair]);
            ELSIF k IN (2, 6) THEN                              -- Couples Massage
                IF (abs(day_offset) + k) % 8 = 0 THEN
                    disc := 15.00; disc_id := d_couples; disc_code := 'COUPLES15';
                END IF;
                slot := (the_day + (CASE k WHEN 2 THEN time '11:00' ELSE time '16:00' END)) AT TIME ZONE zone;
                PERFORM pg_temp.seed_reservation(biz, cust, s_couples, slot, 60, 0, 2, status, 80.00, disc, disc_id,
                        disc_code, src,
                        CASE k WHEN 2 THEN ARRAY[room2, maria, mark, chair2, chair3]
                               ELSE ARRAY[room2, anna, maria, chair1, chair2] END,
                        ARRAY[rq_couples_room, rq_couples_emp, rq_couples_emp, rq_couples_chair, rq_couples_chair]);
            ELSE                                                -- Facial Treatment (k = 3, 5)
                slot := (the_day + (CASE k WHEN 3 THEN time '13:00' ELSE time '15:00' END)) AT TIME ZONE zone;
                PERFORM pg_temp.seed_reservation(biz, cust, s_facial, slot, 45, 15, 1, status, 35.00, 0, NULL, NULL,
                        src, ARRAY[room3, facial_emp], ARRAY[rq_facial_room, rq_facial_emp]);
            END IF;
        END LOOP;
    END LOOP;

    -- Usage counters follow the reservations that actually hold a discount.
    UPDATE discount d SET usage_count = (
        SELECT count(*) FROM reservation r WHERE r.discount_id = d.id AND r.status <> 'CANCELLED')
    WHERE d.business_id = biz;

    -- =======================================================================
    -- Tenant 2: Harbor Bistro (restaurant) — same model, different business
    -- =======================================================================
    INSERT INTO app_user (email, password_hash, full_name)
    VALUES ('owner@harbor-bistro.com', demo_password, 'Luka Petrovski') RETURNING id INTO bistro_owner;

    INSERT INTO business (name, slug, description, email, phone, address, currency, timezone)
    VALUES ('Harbor Bistro', 'harbor-bistro',
            'Seasonal Mediterranean cooking by the lake. Reserve a table for lunch or dinner.',
            'table@harbor-bistro.com', '+389 46 262 626', 'Kej Makedonija 5, Ohrid', 'EUR', zone)
    RETURNING id INTO bistro;

    INSERT INTO business_user (business_id, user_id, role) VALUES (bistro, bistro_owner, 'BUSINESS_OWNER');
    INSERT INTO business_settings (business_id, minimum_booking_notice_minutes, slot_interval_minutes)
    VALUES (bistro, 120, 30);

    -- Tue-Sun, lunch and dinner service with a break in between.
    INSERT INTO business_hours (business_id, day_of_week, start_time, end_time)
    SELECT bistro, d, '12:00', '15:30' FROM generate_series(2, 7) AS d;
    INSERT INTO business_hours (business_id, day_of_week, start_time, end_time)
    SELECT bistro, d, '18:00', '23:00' FROM generate_series(2, 7) AS d;

    INSERT INTO resource_type (business_id, name, description)
    VALUES (bistro, 'Table', 'Capacity is the number of seats.') RETURNING id INTO t_table;
    INSERT INTO resource_type (business_id, name, description, is_employee_type)
    VALUES (bistro, 'Employee', 'Team members. Managed on the Employees page.', TRUE);

    WITH inserted AS (
        INSERT INTO resource (business_id, resource_type_id, name, description, capacity) VALUES
            (bistro, t_table, 'Table 1', 'Window, two seats', 2),
            (bistro, t_table, 'Table 2', 'Window, two seats', 2),
            (bistro, t_table, 'Table 3', 'Main room', 4),
            (bistro, t_table, 'Table 4', 'Main room', 4),
            (bistro, t_table, 'Table 5', 'Corner booth', 6),
            (bistro, t_table, 'Terrace', 'Lake view, eight seats', 8)
        RETURNING id
    )
    SELECT array_agg(id ORDER BY id) INTO tbl FROM inserted;

    INSERT INTO service (business_id, name, description, duration_minutes, price, price_per_participant,
                         max_participants, allow_customer_resource_selection)
    VALUES (bistro, 'Dinner', 'Three-course seasonal menu. Price per guest.', 120, 35.00, TRUE, 8, TRUE)
    RETURNING id INTO s_dinner;
    INSERT INTO service (business_id, name, description, duration_minutes, price, price_per_participant,
                         max_participants, allow_customer_resource_selection)
    VALUES (bistro, 'Lunch', 'Two-course lunch menu. Price per guest.', 90, 22.00, TRUE, 6, TRUE)
    RETURNING id INTO s_lunch;

    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose)
    VALUES (s_dinner, t_table, 1, TRUE, TRUE) RETURNING id INTO rq_dinner;
    INSERT INTO service_resource_requirement (service_id, resource_type_id, quantity, required, customer_can_choose)
    VALUES (s_lunch, t_table, 1, TRUE, TRUE) RETURNING id INTO rq_lunch;

    WITH inserted AS (
        INSERT INTO customer (business_id, first_name, last_name, email, phone) VALUES
            (bistro, 'Petra',  'Novak',   'petra.novak@example.com',  '+389 75 300 001'),
            (bistro, 'Aleks',  'Mitrev',  'aleks.mitrev@example.com', '+389 75 300 002'),
            (bistro, 'Hana',   'Kovac',   'hana.kovac@example.com',   '+389 75 300 003'),
            (bistro, 'Oliver', 'Grant',   'oliver.grant@example.com', '+389 75 300 004')
        RETURNING id
    )
    SELECT array_agg(id ORDER BY id) INTO guests FROM inserted;

    FOR day_offset IN -21 .. 10 LOOP
        the_day := current_date + day_offset;
        dow := extract(isodow FROM the_day);
        CONTINUE WHEN dow = 1;                                   -- closed on Monday
        status := CASE WHEN day_offset < 0 THEN 'COMPLETED' ELSE 'CONFIRMED' END;

        -- Dinner for two at a two-top, and a party of five in the booth.
        slot := (the_day + time '19:00') AT TIME ZONE zone;
        PERFORM pg_temp.seed_reservation(bistro, guests[(abs(day_offset) % 4) + 1], s_dinner, slot, 120, 0, 2,
                status, 70.00, 0, NULL, NULL, 'PUBLIC', ARRAY[tbl[1]], ARRAY[rq_dinner]);
        IF day_offset % 2 = 0 THEN
            PERFORM pg_temp.seed_reservation(bistro, guests[((abs(day_offset) + 2) % 4) + 1], s_dinner, slot, 120, 0,
                    5, status, 175.00, 0, NULL, NULL, 'ADMIN', ARRAY[tbl[5]], ARRAY[rq_dinner]);
        END IF;
        -- Lunch for four.
        IF day_offset % 3 = 0 THEN
            slot := (the_day + time '13:00') AT TIME ZONE zone;
            PERFORM pg_temp.seed_reservation(bistro, guests[((abs(day_offset) + 1) % 4) + 1], s_lunch, slot, 90, 0,
                    4, status, 88.00, 0, NULL, NULL, 'PUBLIC', ARRAY[tbl[3]], ARRAY[rq_lunch]);
        END IF;
    END LOOP;
END $$;
