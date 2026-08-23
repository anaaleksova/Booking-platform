package com.bookingsaas.service.dto;

import com.bookingsaas.service.BookableService;

import java.math.BigDecimal;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency,
        int bufferBeforeMinutes,
        int bufferAfterMinutes,
        boolean requiresStaff,
        boolean requiresResource,
        boolean active
) {

    public static ServiceResponse from(BookableService service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getDurationMinutes(), service.getPrice(), service.getCurrency(),
                service.getBufferBeforeMinutes(), service.getBufferAfterMinutes(),
                service.isRequiresStaff(), service.isRequiresResource(), service.isActive());
    }
}
