--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 025: customer phone numbers in one form (E.164, "+38970555444"), as chat
-- bookings already store them, so a customer is recognised by phone whatever
-- channel they book through. New numbers are stored this way by
-- CustomerService; this brings the existing ones in line.
--
-- Only numbers with a country code are rewritten. Where two customers of a
-- business would end up with the same number, neither is changed: which
-- record is the real one is for the business to decide.
-- ---------------------------------------------------------------------------

--changeset booking-platform:025-customer-phone-e164
UPDATE customer c
SET phone = n.normalized
FROM (
    SELECT id,
           '+' || regexp_replace(phone, '[^0-9]', '', 'g') AS normalized,
           count(*) OVER (PARTITION BY business_id, '+' || regexp_replace(phone, '[^0-9]', '', 'g')) AS same_number
    FROM customer
    WHERE phone LIKE '+%'
) n
WHERE c.id = n.id
  AND n.same_number = 1
  AND c.phone <> n.normalized;
--rollback SELECT 1;
