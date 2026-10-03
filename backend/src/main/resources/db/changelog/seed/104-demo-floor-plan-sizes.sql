--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Harbor Bistro's demo tables at a size that reads well with chairs drawn
-- around them (dev context only).
-- ---------------------------------------------------------------------------

--changeset booking-platform:104-demo-floor-plan-sizes context:dev
UPDATE floor_plan_item i
SET width = p.w, height = p.h
FROM resource r
JOIN business b ON b.id = r.business_id AND b.slug = 'harbor-bistro'
JOIN (VALUES
    ('Table 1', 12, 19.2),
    ('Table 2', 12, 19.2),
    ('Table 3', 14, 22.4),
    ('Table 4', 14, 22.4),
    ('Table 5', 18, 26),
    ('Terrace', 11, 60)
) AS p(name, w, h) ON p.name = r.name
WHERE i.resource_id = r.id;
