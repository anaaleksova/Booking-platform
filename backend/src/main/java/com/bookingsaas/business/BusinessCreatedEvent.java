package com.bookingsaas.business;

/**
 * Published inside the transaction that creates a business, so other modules
 * can provision their per-tenant defaults (the employee resource type) without
 * the business module depending on them.
 */
public record BusinessCreatedEvent(Long businessId) {
}
