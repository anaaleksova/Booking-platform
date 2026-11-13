package com.bookingsaas.resource.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Gives other resources of the same type this one's weekly schedule.
 *
 * @param includeServices also copy which services it serves; null = no
 */
public record CopyScheduleRequest(
        @NotEmpty @Size(max = 200) List<Long> targetIds,
        Boolean includeServices
) {
}
