package com.bookingsaas.publicapi.dto;

import com.bookingsaas.service.BookableService;

import java.math.BigDecimal;

/** A bookable service as shown on the public page — no buffers, no internals. */
public record PublicServiceResponse(
        Long id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency,
        boolean requiresStaff
) {

    public static PublicServiceResponse from(BookableService service) {
        return new PublicServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getDurationMinutes(), service.getPrice(), service.getCurrency(),
                service.isRequiresStaff());
    }
}
