--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 032: "Connect with Facebook". The owner signs in with Meta and picks their
-- Page; the platform keeps that Page's access token (encrypted) so it can
-- answer the Page's Messenger and Instagram chats for this business.
-- ---------------------------------------------------------------------------

--changeset booking-platform:032-meta-connection
CREATE TABLE meta_connection (
    id                    BIGSERIAL PRIMARY KEY,
    business_id           BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    page_id               VARCHAR(64)   NOT NULL,
    page_name             VARCHAR(255)  NOT NULL,
    -- The Instagram professional account linked to the Page, when there is one.
    instagram_account_id  VARCHAR(64),
    instagram_username    VARCHAR(255),
    -- AES-GCM, key from META_TOKEN_KEY; never returned by the admin API.
    page_token_encrypted  VARCHAR(2000) NOT NULL,
    connected_by_user_id  BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_meta_connection_business UNIQUE (business_id),
    -- One Page answers for one business, or its messages would have two owners.
    CONSTRAINT uq_meta_connection_page UNIQUE (page_id)
);

--changeset booking-platform:032-meta-connect-session
-- An owner who manages several Pages chooses one after signing in; until then
-- their Meta user token waits here for a few minutes.
CREATE TABLE meta_connect_session (
    id                    UUID          PRIMARY KEY,
    business_id           BIGINT        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id               BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    user_token_encrypted  VARCHAR(2000) NOT NULL,
    expires_at            TIMESTAMPTZ   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_meta_connect_session_expires ON meta_connect_session (expires_at);
