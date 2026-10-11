package com.bookingsaas.integration.auth;

/**
 * What an integration token may do. Deliberately narrow: none of these
 * reaches the admin API, and every one is limited to the token's business.
 */
public enum IntegrationPermission {
    READ_BUSINESS,
    READ_SERVICES,
    READ_AVAILABILITY,
    CREATE_RESERVATION,
    READ_RESERVATION,
    CANCEL_RESERVATION,
    READ_CUSTOMER,
    CREATE_CUSTOMER,
    /** Read and advance chat conversations with customers. */
    MANAGE_CONVERSATIONS
}
