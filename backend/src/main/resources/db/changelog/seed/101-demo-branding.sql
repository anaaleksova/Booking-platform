--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- Demo booking-page content (dev context only): taglines, accent colours and
-- service categories, so the customer pages look like a configured business.
-- Photos are uploaded through the admin panel.
-- ---------------------------------------------------------------------------

--changeset booking-platform:101-demo-branding context:dev
UPDATE business
SET tagline = 'Massages and facials in the heart of the city — book your moment of calm.',
    accent_color = '#b4536a',
    instagram_url = 'https://instagram.com/',
    show_map = TRUE
WHERE slug = 'serenity-studio';

UPDATE business
SET tagline = 'Seasonal Mediterranean cooking on the shore of Lake Ohrid.',
    accent_color = '#1f6f8b',
    show_map = TRUE
WHERE slug = 'harbor-bistro';

UPDATE service SET category = 'Massages'
WHERE name IN ('Classic Massage', 'Couples Massage')
  AND business_id = (SELECT id FROM business WHERE slug = 'serenity-studio');

UPDATE service SET category = 'Face care'
WHERE name = 'Facial Treatment'
  AND business_id = (SELECT id FROM business WHERE slug = 'serenity-studio');

UPDATE service SET category = 'Tables'
WHERE business_id = (SELECT id FROM business WHERE slug = 'harbor-bistro');
