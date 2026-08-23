-- ---------------------------------------------------------------------------
-- Development seed data (dev profile only — see application-dev.yml).
--
-- Two tenants on purpose: a single-tenant seed makes it impossible to notice
-- when a query has lost its business_id filter. "Downtown Barbers" exists so
-- that cross-tenant leaks are visible while clicking around.
--
-- Login for both: password123
--   owner@demo-pilates.mk   -> Demo Pilates Studio
--   owner@downtown.mk       -> Downtown Barbers
--
-- Repeatable migration, and a no-op once the demo business exists, so
-- restarting the app never duplicates or resets hand-made test data.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    -- BCrypt hash of 'password123'. A development-only credential; real
    -- accounts are hashed by the application at registration.
    demo_password  TEXT := '$2a$10$4HYzuABux1HvCXWygtMESuWWGsAzB.LNSJB2mhlg5vTefVmMvTYaW';
    zone           TEXT := 'Europe/Skopje';

    owner_id       BIGINT;
    business_id    BIGINT;
    svc_reformer   BIGINT;
    svc_mat        BIGINT;
    svc_personal   BIGINT;
    svc_rental     BIGINT;
    staff_ana      BIGINT;
    staff_elena    BIGINT;
    staff_marija   BIGINT;
    res_ref1       BIGINT;
    res_ref2       BIGINT;
    res_ref3       BIGINT;
    cust_ana       BIGINT;
    cust_marko     BIGINT;
    cust_sara      BIGINT;

    barber_owner   BIGINT;
    barber_biz     BIGINT;
    barber_service BIGINT;
    barber_staff   BIGINT;

    slot_start     TIMESTAMPTZ;
BEGIN
    IF EXISTS (SELECT 1 FROM businesses WHERE slug = 'demo-pilates-studio') THEN
        RETURN;
    END IF;

    -- === Tenant 1: Demo Pilates Studio ====================================
    INSERT INTO users (email, password_hash, full_name)
    VALUES ('owner@demo-pilates.mk', demo_password, 'Ana Aleksova')
    RETURNING id INTO owner_id;

    INSERT INTO businesses (name, slug, description, phone, email, address, timezone)
    VALUES ('Demo Pilates Studio', 'demo-pilates-studio',
            'Reformer and mat pilates in the centre of Skopje. Small groups, individual attention.',
            '+389 70 123 456', 'hello@demo-pilates.mk', 'Bul. Partizanski Odredi 15, Skopje', zone)
    RETURNING id INTO business_id;

    INSERT INTO business_users (business_id, user_id, role) VALUES (business_id, owner_id, 'BUSINESS_OWNER');

    INSERT INTO business_settings (business_id, minimum_booking_notice_minutes, maximum_booking_advance_days,
                                   slot_interval_minutes, allow_customer_cancellation, cancellation_deadline_hours)
    VALUES (business_id, 120, 60, 15, TRUE, 12);

    -- Mon-Fri 08:00-20:00 with a 13:00-14:00 lunch break expressed as two
    -- intervals; Saturday mornings only; closed Sunday.
    INSERT INTO working_hours (business_id, day_of_week, start_time, end_time)
    SELECT business_id, d, '08:00', '13:00' FROM generate_series(1, 5) AS d;
    INSERT INTO working_hours (business_id, day_of_week, start_time, end_time)
    SELECT business_id, d, '14:00', '20:00' FROM generate_series(1, 5) AS d;
    INSERT INTO working_hours (business_id, day_of_week, start_time, end_time)
    VALUES (business_id, 6, '09:00', '14:00');

    -- Services: one needs staff + a reformer, one needs only staff, one needs
    -- only a resource. Three shapes, one engine.
    INSERT INTO services (business_id, name, description, duration_minutes, price, currency,
                          buffer_before_minutes, buffer_after_minutes, requires_staff, requires_resource)
    VALUES (business_id, 'Reformer Pilates', 'Guided reformer session, 1-on-1.', 50, 900, 'MKD', 0, 10, TRUE, TRUE)
    RETURNING id INTO svc_reformer;

    INSERT INTO services (business_id, name, description, duration_minutes, price, currency,
                          buffer_before_minutes, buffer_after_minutes, requires_staff, requires_resource)
    VALUES (business_id, 'Mat Pilates', 'Mat-based class for all levels.', 60, 600, 'MKD', 0, 0, TRUE, FALSE)
    RETURNING id INTO svc_mat;

    INSERT INTO services (business_id, name, description, duration_minutes, price, currency,
                          buffer_before_minutes, buffer_after_minutes, requires_staff, requires_resource)
    VALUES (business_id, 'Personal Training', 'One-to-one strength and mobility.', 45, 1200, 'MKD', 5, 10, TRUE, FALSE)
    RETURNING id INTO svc_personal;

    INSERT INTO services (business_id, name, description, duration_minutes, price, currency,
                          buffer_before_minutes, buffer_after_minutes, requires_staff, requires_resource)
    VALUES (business_id, 'Studio Rental', 'Rent the studio space by the hour. No instructor.',
            60, 1500, 'MKD', 0, 15, FALSE, TRUE)
    RETURNING id INTO svc_rental;

    INSERT INTO staff (business_id, user_id, name, email, phone)
    VALUES (business_id, owner_id, 'Ana Aleksova', 'ana@demo-pilates.mk', '+389 70 123 456')
    RETURNING id INTO staff_ana;

    INSERT INTO staff (business_id, name, email, phone)
    VALUES (business_id, 'Elena Petrova', 'elena@demo-pilates.mk', '+389 70 222 333')
    RETURNING id INTO staff_elena;

    -- No account and no email: the common case of a staff member who only
    -- exists on the schedule.
    INSERT INTO staff (business_id, name)
    VALUES (business_id, 'Marija Stojanova')
    RETURNING id INTO staff_marija;

    INSERT INTO staff_services (staff_id, service_id) VALUES
        (staff_ana, svc_reformer), (staff_ana, svc_mat), (staff_ana, svc_personal),
        (staff_elena, svc_reformer), (staff_elena, svc_mat),
        (staff_marija, svc_mat), (staff_marija, svc_personal);

    -- Elena works later than the studio opens; Marija only three days a week.
    INSERT INTO staff_working_hours (business_id, staff_id, day_of_week, start_time, end_time)
    SELECT business_id, staff_elena, d, '10:00', '20:00' FROM generate_series(1, 5) AS d;
    INSERT INTO staff_working_hours (business_id, staff_id, day_of_week, start_time, end_time)
    SELECT business_id, staff_marija, d, '08:00', '16:00' FROM generate_series(1, 3) AS d;

    INSERT INTO resources (business_id, name, description) VALUES (business_id, 'Reformer 1', 'Window side')
    RETURNING id INTO res_ref1;
    INSERT INTO resources (business_id, name, description) VALUES (business_id, 'Reformer 2', 'Middle')
    RETURNING id INTO res_ref2;
    INSERT INTO resources (business_id, name, description) VALUES (business_id, 'Reformer 3', 'Back of the studio')
    RETURNING id INTO res_ref3;

    INSERT INTO resource_services (resource_id, service_id) VALUES
        (res_ref1, svc_reformer), (res_ref2, svc_reformer), (res_ref3, svc_reformer);

    INSERT INTO customers (business_id, name, phone, email) VALUES
        (business_id, 'Marko Ilievski', '+389 71 555 111', 'marko@example.com') RETURNING id INTO cust_marko;
    INSERT INTO customers (business_id, name, phone, email) VALUES
        (business_id, 'Sara Nikolova', '+389 71 555 222', 'sara@example.com') RETURNING id INTO cust_sara;
    INSERT INTO customers (business_id, name, phone, email) VALUES
        (business_id, 'Ana Trajkova', '+389 71 555 333', 'ana.t@example.com') RETURNING id INTO cust_ana;

    -- A blocked afternoon for Elena, so availability visibly differs per staff.
    INSERT INTO blocked_times (business_id, staff_id, start_date_time, end_date_time, reason)
    VALUES (business_id, staff_elena,
            ((current_date + 2) + time '14:00') AT TIME ZONE zone,
            ((current_date + 2) + time '20:00') AT TIME ZONE zone,
            'Workshop');

    -- === Bookings =========================================================
    -- Yesterday, completed.
    slot_start := ((current_date - 1) + time '10:00') AT TIME ZONE zone;
    INSERT INTO bookings (business_id, service_id, staff_id, resource_id, customer_id,
                          start_date_time, end_date_time, block_start_date_time, block_end_date_time,
                          status, price, currency, source)
    VALUES (business_id, svc_reformer, staff_ana, res_ref1, cust_marko,
            slot_start, slot_start + interval '50 minutes',
            slot_start, slot_start + interval '60 minutes',
            'COMPLETED', 900, 'MKD', 'PUBLIC');

    -- Today, confirmed.
    slot_start := (current_date + time '11:00') AT TIME ZONE zone;
    INSERT INTO bookings (business_id, service_id, staff_id, customer_id,
                          start_date_time, end_date_time, block_start_date_time, block_end_date_time,
                          status, price, currency, source, customer_notes)
    VALUES (business_id, svc_mat, staff_marija, cust_sara,
            slot_start, slot_start + interval '60 minutes',
            slot_start, slot_start + interval '60 minutes',
            'CONFIRMED', 600, 'MKD', 'DASHBOARD', 'First class — please explain the basics.');

    -- Tomorrow, confirmed, on a reformer.
    slot_start := ((current_date + 1) + time '09:00') AT TIME ZONE zone;
    INSERT INTO bookings (business_id, service_id, staff_id, resource_id, customer_id,
                          start_date_time, end_date_time, block_start_date_time, block_end_date_time,
                          status, price, currency, source)
    VALUES (business_id, svc_reformer, staff_ana, res_ref2, cust_ana,
            slot_start, slot_start + interval '50 minutes',
            slot_start, slot_start + interval '60 minutes',
            'CONFIRMED', 900, 'MKD', 'PUBLIC');

    -- Next week, cancelled — so the calendar shows a released slot.
    slot_start := ((current_date + 7) + time '15:00') AT TIME ZONE zone;
    INSERT INTO bookings (business_id, service_id, staff_id, customer_id,
                          start_date_time, end_date_time, block_start_date_time, block_end_date_time,
                          status, price, currency, source, cancelled_at, cancellation_reason)
    VALUES (business_id, svc_personal, staff_ana, cust_marko,
            slot_start, slot_start + interval '45 minutes',
            slot_start - interval '5 minutes', slot_start + interval '55 minutes',
            'CANCELLED', 1200, 'MKD', 'PUBLIC', now(), 'Customer is travelling');

    -- === Tenant 2: Downtown Barbers =======================================
    INSERT INTO users (email, password_hash, full_name)
    VALUES ('owner@downtown.mk', demo_password, 'Bojan Ristov')
    RETURNING id INTO barber_owner;

    INSERT INTO businesses (name, slug, description, phone, email, address, timezone)
    VALUES ('Downtown Barbers', 'downtown-barbers', 'Classic cuts and hot-towel shaves.',
            '+389 75 987 654', 'hello@downtown.mk', 'Ulica Makedonija 8, Skopje', zone)
    RETURNING id INTO barber_biz;

    INSERT INTO business_users (business_id, user_id, role) VALUES (barber_biz, barber_owner, 'BUSINESS_OWNER');
    INSERT INTO business_settings (business_id) VALUES (barber_biz);

    INSERT INTO working_hours (business_id, day_of_week, start_time, end_time)
    SELECT barber_biz, d, '09:00', '19:00' FROM generate_series(1, 6) AS d;

    INSERT INTO services (business_id, name, duration_minutes, price, currency, buffer_after_minutes)
    VALUES (barber_biz, 'Haircut', 45, 600, 'MKD', 5)
    RETURNING id INTO barber_service;

    INSERT INTO staff (business_id, user_id, name) VALUES (barber_biz, barber_owner, 'Bojan Ristov')
    RETURNING id INTO barber_staff;

    INSERT INTO staff_services (staff_id, service_id) VALUES (barber_staff, barber_service);

    INSERT INTO customers (business_id, name, phone) VALUES (barber_biz, 'Stefan Petrov', '+389 78 111 222');
END $$;
