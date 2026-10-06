--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Harbor Bistro (dev context only): tables are pushed together for larger
-- groups, so dinner takes parties of up to twelve and lunch up to ten.
-- ---------------------------------------------------------------------------

--changeset booking-platform:107-demo-combined-tables context:dev
UPDATE service_resource_requirement srr
SET combine_resources = TRUE
FROM service s
         JOIN business b ON b.id = s.business_id
WHERE s.id = srr.service_id AND b.slug = 'harbor-bistro';

UPDATE service s
SET max_participants = CASE s.name WHEN 'Dinner' THEN 12 ELSE 10 END
FROM business b
WHERE b.id = s.business_id AND b.slug = 'harbor-bistro' AND s.name IN ('Dinner', 'Lunch');
