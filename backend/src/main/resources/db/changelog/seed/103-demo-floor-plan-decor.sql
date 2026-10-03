--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Dresses Harbor Bistro's demo floor plan (dev context only): the dining room
-- with its bar, kitchen and entrance, and the terrace behind a half wall.
-- ---------------------------------------------------------------------------

--changeset booking-platform:103-demo-floor-plan-decor context:dev splitStatements:false
DO $$
DECLARE
    plan BIGINT;
BEGIN
    SELECT fp.id INTO plan
    FROM floor_plan fp
    JOIN business b ON b.id = fp.business_id
    JOIN resource_type rt ON rt.id = fp.resource_type_id
    WHERE b.slug = 'harbor-bistro' AND rt.name = 'Table';
    IF plan IS NULL THEN
        RETURN;
    END IF;

    UPDATE floor_plan_item i
    SET x = p.x, y = p.y, width = p.w, height = p.h, shape = p.shape
    FROM resource r
    JOIN (VALUES
        ('Table 1', 12, 22, 10, 16, 'ROUND'),
        ('Table 2', 12, 52, 10, 16, 'ROUND'),
        ('Table 3', 35, 26, 12, 19.2, 'SQUARE'),
        ('Table 4', 35, 62, 12, 19.2, 'SQUARE'),
        ('Table 5', 58, 60, 16, 22, 'RECT'),
        ('Terrace', 88.5, 50, 10, 56, 'RECT')
    ) AS p(name, x, y, w, h, shape) ON p.name = r.name
    WHERE i.floor_plan_id = plan AND i.resource_id = r.id;

    INSERT INTO floor_plan_decor (floor_plan_id, kind, label, tone, x, y, width, height) VALUES
        (plan, 'ZONE', 'Terrace', 'GREEN', 88.5, 50, 21, 96),
        (plan, 'ZONE', 'Kitchen', 'SAND', 12, 87, 22, 22),
        (plan, 'WALL', NULL, NULL, 77, 17, 1, 34),
        (plan, 'WALL', NULL, NULL, 77, 83, 1, 34),
        (plan, 'COUNTER', 'Bar', NULL, 57, 15, 24, 10),
        (plan, 'ENTRANCE', 'Entrance', NULL, 45, 96, 10, 7),
        (plan, 'PLANT', NULL, NULL, 4.5, 8, 4.5, 7.2),
        (plan, 'PLANT', NULL, NULL, 71, 91, 4.5, 7.2),
        (plan, 'PLANT', NULL, NULL, 96, 7, 4.5, 7.2),
        (plan, 'PLANT', NULL, NULL, 96, 93, 4.5, 7.2);
END $$;
