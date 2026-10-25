package com.bookingsaas.platform;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** What the platform area shows and accepts. */
public final class PlatformDtos {

    private PlatformDtos() {
    }

    /**
     * @param reservationsThisMonth made this calendar month (UTC), across every business
     * @param newBusinesses         created in the last 30 days
     */
    public record Overview(long businesses, long activeBusinesses, long accounts, long reservationsThisMonth,
                           long newBusinesses) {
    }

    /** @param recentReservations made in the last 30 days */
    public record BusinessRow(Long id, String name, String slug, boolean active, Instant createdAt,
                              List<String> ownerEmails, long recentReservations) {
    }

    /**
     * @param roles         "Owner · Harbor Bistro", "Staff · Serenity Studio"
     * @param customerAt    how many businesses know this email as a customer
     * @param platformAdmin runs the platform; cannot be deactivated from here
     */
    public record AccountRow(Long id, String email, String fullName, boolean active, Instant createdAt,
                             List<String> roles, long customerAt, boolean platformAdmin) {
    }

    /** @param action e.g. ENTERED_BUSINESS, BUSINESS_DEACTIVATED, ACCOUNT_REACTIVATED */
    public record ActivityRow(Long id, String actorEmail, String action, String targetType, Long targetId,
                              String targetName, Instant createdAt) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }
}
