--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 022: inviting employees to log in. The owner invites an employee by email;
-- the link in that email (its secret stored only as a hash) lets the person
-- set a password, or use an account they already have, and makes them STAFF
-- of the business, linked to that employee. One open invitation per employee.
-- ---------------------------------------------------------------------------

--changeset booking-platform:022-staff-invitations
CREATE TABLE staff_invitation (
    id                  BIGSERIAL PRIMARY KEY,
    business_id         BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    employee_id         BIGINT       NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    email               VARCHAR(320) NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    invited_by_user_id  BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_invitation_employee UNIQUE (employee_id),
    CONSTRAINT uq_staff_invitation_token UNIQUE (token_hash)
);
CREATE INDEX idx_staff_invitation_business ON staff_invitation (business_id);
--rollback DROP TABLE staff_invitation;
