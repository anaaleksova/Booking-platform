package com.bookingsaas.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A reservation as the admin panel sees it.
 *
 * <p>Instants are the truth; the {@code local*} fields are the same moments
 * already rendered in the business timezone, so the calendar carries no
 * timezone maths of its own.
 */
public record ReservationResponse(
        Long id,
        UUID reference,
        Long serviceId,
        String serviceName,
        Long customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        int numberOfParticipants,
        Instant startTime,
        Instant endTime,
        LocalDateTime localStartTime,
        LocalDateTime localEndTime,
        String timezone,
        String status,
        String source,
        BigDecimal originalPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        String discountCode,
        List<AssignedResource> resources,
        String customerNotes,
        String internalNotes,
        Instant createdAt,
        Instant cancelledAt,
        String cancellationReason
) {

    public record AssignedResource(
            Long resourceId,
            String name,
            Long resourceTypeId,
            String resourceTypeName,
            Long requirementId
    ) {
    }
}
