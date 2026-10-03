package com.bookingsaas.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Resources picked for one requirement of the service, e.g. "Employee: Anna, Maria". */
public record ResourceSelection(
        @NotNull Long requirementId,
        @NotNull @Size(max = 50) List<@NotNull Long> resourceIds
) {
}
