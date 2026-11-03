--liquibase formatted sql

-- Demo: the two demo businesses say what they are, like a business signed
-- up from a template. Harbor Bistro keeps its events; Serenity, a spa, has none.

--changeset booking-platform:111-demo-business-types context:dev
UPDATE business SET business_type = 'RESTAURANT' WHERE slug = 'harbor-bistro';
UPDATE business SET business_type = 'SPA' WHERE slug = 'serenity-studio';
