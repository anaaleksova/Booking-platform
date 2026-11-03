package com.bookingsaas.business;

/**
 * Published inside the transaction that creates a business, so other modules
 * can provision their per-tenant defaults (the employee resource type, the
 * template's resources and services) without the business module depending
 * on them.
 *
 * @param type        what the owner said the business is; never null
 * @param ownerUserId the account that signed up
 * @param ownerName   their name, for templates that make the owner the first employee
 */
public record BusinessCreatedEvent(Long businessId, BusinessType type, Long ownerUserId, String ownerName) {
}
