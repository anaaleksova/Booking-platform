package com.bookingsaas.integration;

public enum IntegrationStatus {
    NOT_CONNECTED,
    CONNECTED,
    /** Configured but switched off: nothing is sent to it. */
    DISABLED
}
