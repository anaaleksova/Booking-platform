--liquibase formatted sql

-- Harbor Bistro's floor plan gets a fitting name now that types can have several (dev only).

--changeset booking-platform:106-demo-floor-names context:dev
UPDATE floor_plan fp
SET name = 'Dining room'
FROM business b
WHERE b.id = fp.business_id AND b.slug = 'harbor-bistro';
