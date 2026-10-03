--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Demo floor plan for Harbor Bistro's tables (dev context only), so the
-- "choose your table on the map" step can be tried straight away.
-- ---------------------------------------------------------------------------

--changeset booking-platform:102-demo-floor-plan context:dev splitStatements:false
DO $$
DECLARE
    bistro BIGINT;
    table_type BIGINT;
    plan BIGINT;
BEGIN
    SELECT id INTO bistro FROM business WHERE slug = 'harbor-bistro';
    IF bistro IS NULL THEN
        RETURN;
    END IF;
    SELECT id INTO table_type FROM resource_type WHERE business_id = bistro AND name = 'Table';

    INSERT INTO floor_plan (business_id, resource_type_id, aspect_ratio)
    VALUES (bistro, table_type, 1.6) RETURNING id INTO plan;

    -- Two window two-tops on the left, four-tops in the middle, the booth and
    -- the long terrace table on the right.
    INSERT INTO floor_plan_item (floor_plan_id, resource_id, x, y, width, height, shape)
    SELECT plan, r.id, p.x, p.y, p.w, p.h, p.shape
    FROM resource r
    JOIN (VALUES
        ('Table 1', 12, 25, 9, 14, 'ROUND'),
        ('Table 2', 12, 70, 9, 14, 'ROUND'),
        ('Table 3', 38, 30, 12, 19, 'SQUARE'),
        ('Table 4', 38, 70, 12, 19, 'SQUARE'),
        ('Table 5', 63, 50, 14, 26, 'SQUARE'),
        ('Terrace', 87, 50, 10, 60, 'RECT')
    ) AS p(name, x, y, w, h, shape) ON p.name = r.name
    WHERE r.business_id = bistro AND r.resource_type_id = table_type;
END $$;
