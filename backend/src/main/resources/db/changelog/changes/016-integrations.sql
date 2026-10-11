--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 016: the integration layer — automation tools such as n8n talking to the
-- platform on behalf of a business, and customers reaching it from social
-- channels.
--
-- The platform stays the source of truth: nothing here holds booking rules,
-- only who may call the API, who a social user is, where a conversation
-- stands and which events still have to be delivered.
-- ---------------------------------------------------------------------------

--changeset booking-platform:016-integration
-- One row per business and provider: the n8n webhook today, OAuth-connected
-- channels later. Secrets for OAuth tokens will need their own encrypted
-- storage; nothing like a password is ever kept here.
CREATE TABLE integration (
    id                  BIGSERIAL PRIMARY KEY,
    business_id         BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    provider            VARCHAR(16)   NOT NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'NOT_CONNECTED',
    -- n8n: where outbound events are posted.
    webhook_url         VARCHAR(1000),
    -- n8n: HMAC key that signs outbound events so n8n can reject forgeries.
    signing_secret      VARCHAR(128),
    -- Instagram / Facebook: the page or account id messages arrive for.
    external_account_id VARCHAR(128),
    display_name        VARCHAR(160),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_business_provider UNIQUE (business_id, provider),
    CONSTRAINT ck_integration_provider CHECK (provider IN ('N8N', 'INSTAGRAM', 'FACEBOOK')),
    CONSTRAINT ck_integration_status CHECK (status IN ('NOT_CONNECTED', 'CONNECTED', 'DISABLED'))
);

--changeset booking-platform:016-integration-token
-- API tokens for automation tools. Only a SHA-256 of the token is stored; the
-- token itself is shown once, when it is created.
CREATE TABLE integration_token (
    id                 BIGSERIAL PRIMARY KEY,
    business_id        BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name               VARCHAR(120)  NOT NULL,
    -- The first characters, so an admin can tell tokens apart.
    token_prefix       VARCHAR(16)   NOT NULL,
    token_hash         VARCHAR(64)   NOT NULL,
    -- Comma-separated IntegrationPermission names.
    permissions        VARCHAR(500)  NOT NULL,
    created_by_user_id BIGINT        REFERENCES app_user (id) ON DELETE SET NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_integration_token_business ON integration_token (business_id);

--changeset booking-platform:016-customer-external-identity
-- "Instagram user abc123 is customer 42 at this business." Social ids are
-- never assumed to be customer ids, and are scoped per business like the
-- customers themselves.
CREATE TABLE customer_external_identity (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    customer_id      BIGINT        NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    provider         VARCHAR(16)   NOT NULL,
    external_user_id VARCHAR(128)  NOT NULL,
    display_name     VARCHAR(160),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_external_identity UNIQUE (business_id, provider, external_user_id),
    CONSTRAINT ck_customer_external_identity_provider CHECK (provider IN ('INSTAGRAM', 'FACEBOOK', 'TEST'))
);

CREATE INDEX idx_customer_external_identity_customer ON customer_external_identity (customer_id);

--changeset booking-platform:016-idempotency
-- A retried "create reservation" with the same key returns the first result
-- instead of booking twice. The key is claimed first, in the reservation's
-- transaction: a simultaneous retry waits on the unique index, then finds the
-- finished reservation — and a failed attempt releases the key on rollback.
CREATE TABLE integration_idempotency (
    id              BIGSERIAL PRIMARY KEY,
    business_id     BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128)  NOT NULL,
    operation       VARCHAR(32)   NOT NULL,
    -- SHA-256 of the request, so a key reused for a different booking is refused.
    request_hash    VARCHAR(64)   NOT NULL,
    -- Filled in once the reservation exists, in the same transaction.
    reservation_id  BIGINT        REFERENCES reservation (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_idempotency UNIQUE (business_id, operation, idempotency_key)
);

--changeset booking-platform:016-conversation
-- Where a chat with a social user stands. The LLM in n8n only interprets
-- messages; the state lives here so a restart, a retry or a forgetful model
-- cannot lose or invent part of a booking.
CREATE TABLE integration_conversation (
    id               BIGSERIAL PRIMARY KEY,
    business_id      BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    provider         VARCHAR(16)   NOT NULL,
    external_user_id VARCHAR(128)  NOT NULL,
    customer_id      BIGINT        REFERENCES customer (id) ON DELETE SET NULL,
    flow             VARCHAR(16)   NOT NULL,
    state            VARCHAR(40)   NOT NULL,
    -- Collected answers and the options last offered, as JSON.
    data             TEXT          NOT NULL DEFAULT '{}',
    reservation_id   BIGINT        REFERENCES reservation (id) ON DELETE SET NULL,
    last_message     VARCHAR(2000),
    last_execution_id VARCHAR(128),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_conversation_provider CHECK (provider IN ('INSTAGRAM', 'FACEBOOK', 'TEST')),
    CONSTRAINT ck_conversation_flow CHECK (flow IN ('BOOKING', 'CANCELLATION'))
);

-- At most one open conversation per person and business.
CREATE UNIQUE INDEX uq_conversation_active
    ON integration_conversation (business_id, provider, external_user_id) WHERE active;
CREATE INDEX idx_conversation_business ON integration_conversation (business_id, updated_at DESC);

--changeset booking-platform:016-integration-event
-- Outbox of events for the automation layer (reservation created, cancelled,
-- reminder due). Written in the reservation's transaction, delivered by a
-- worker, so a down n8n can never fail or lose a booking event.
CREATE TABLE integration_event (
    id              BIGSERIAL PRIMARY KEY,
    business_id     BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    event_type      VARCHAR(40)   NOT NULL,
    reservation_id  BIGINT        REFERENCES reservation (id) ON DELETE CASCADE,
    -- Unique per delivery, sent as a header so n8n can drop duplicates.
    delivery_id     UUID          NOT NULL DEFAULT gen_random_uuid(),
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    response_status INTEGER,
    due_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_event_delivery UNIQUE (delivery_id),
    CONSTRAINT ck_integration_event_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_integration_event_due ON integration_event (status, due_at) WHERE status = 'PENDING';
CREATE INDEX idx_integration_event_business ON integration_event (business_id, created_at DESC);
-- One pending reminder per reservation.
CREATE UNIQUE INDEX uq_integration_event_reminder
    ON integration_event (reservation_id, event_type)
    WHERE event_type = 'reservation.reminder_due' AND status = 'PENDING';
