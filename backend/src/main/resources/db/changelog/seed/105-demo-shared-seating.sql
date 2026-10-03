--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Harbor Bistro (dev context only): the long terrace table is shared — small
-- groups sit together until its eight seats are taken — and the six-seat
-- Table 5 is kept for groups of three or more.
-- ---------------------------------------------------------------------------

--changeset booking-platform:105-demo-shared-seating context:dev
UPDATE resource r
SET shared_seating = TRUE
FROM business b
WHERE b.id = r.business_id AND b.slug = 'harbor-bistro' AND r.name = 'Terrace';

UPDATE resource r
SET min_party_size = 3
FROM business b
WHERE b.id = r.business_id AND b.slug = 'harbor-bistro' AND r.name = 'Table 5';
