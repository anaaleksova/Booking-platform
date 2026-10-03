package com.bookingsaas.service.dto;

import com.bookingsaas.media.Media;
import com.bookingsaas.service.BookableService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        String category,
        UUID imageMediaId,
        String imageUrl,
        int durationMinutes,
        BigDecimal price,
        boolean pricePerParticipant,
        int maxParticipants,
        int bufferBeforeMinutes,
        int bufferAfterMinutes,
        boolean allowCustomerResourceSelection,
        boolean active,
        List<RequirementResponse> requirements
) {

    public record RequirementResponse(
            Long id,
            Long resourceTypeId,
            String resourceTypeName,
            int quantity,
            boolean required,
            boolean customerCanChoose
    ) {
    }

    public static ServiceResponse from(BookableService service, List<RequirementResponse> requirements) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getCategory(), service.getImageMediaId(), Media.urlOf(service.getImageMediaId()),
                service.getDurationMinutes(), service.getPrice(), service.isPricePerParticipant(),
                service.getMaxParticipants(), service.getBufferBeforeMinutes(), service.getBufferAfterMinutes(),
                service.isAllowCustomerResourceSelection(), service.isActive(), requirements);
    }
}
