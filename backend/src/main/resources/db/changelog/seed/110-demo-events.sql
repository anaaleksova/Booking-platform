--liquibase formatted sql

-- Demo: Harbor Bistro has live jazz on the coming Friday (at least two days
-- away), bookable as a dinner and on the booking page from today.

--changeset booking-platform:110-demo-events context:dev
WITH bistro AS (
    SELECT b.id, s.id AS dinner
    FROM business b
    JOIN service s ON s.business_id = b.id AND s.name = 'Dinner'
    WHERE b.slug = 'harbor-bistro'
), friday AS (
    SELECT current_date + (CASE WHEN days < 2 THEN days + 7 ELSE days END) AS day
    FROM (SELECT (5 - extract(isodow FROM current_date)::int + 7) % 7 AS days) d
)
INSERT INTO business_event (business_id, title, description, performer, event_date, start_time, end_time,
                            show_from, show_until, service_id)
SELECT bistro.id, 'Jazz by the lake',
       'An evening of jazz standards on the terrace, with the seasonal dinner menu. Book early: the terrace fills up fast.',
       'Ohrid Jazz Trio', friday.day, '20:00', '23:00', current_date, friday.day, bistro.dinner
FROM bistro, friday;
