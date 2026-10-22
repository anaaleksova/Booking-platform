--liquibase formatted sql

-- Demo: Tomas Berg missed his two most recent past appointments at Serenity Studio.

--changeset booking-platform:109-demo-no-shows context:dev
WITH missed AS (
    SELECT r.id
    FROM reservation r
    JOIN customer c ON c.id = r.customer_id
    JOIN business b ON b.id = r.business_id
    WHERE b.slug = 'serenity-studio'
      AND lower(c.email) = 'tomas.berg@example.com'
      AND r.status = 'COMPLETED'
      AND r.start_time < now()
    ORDER BY r.start_time DESC
    LIMIT 2
), marked AS (
    UPDATE reservation r SET status = 'NO_SHOW'
    FROM missed m WHERE r.id = m.id
    RETURNING r.id
)
UPDATE reservation_resource rr SET occupying = FALSE
FROM marked m WHERE rr.reservation_id = m.id;
