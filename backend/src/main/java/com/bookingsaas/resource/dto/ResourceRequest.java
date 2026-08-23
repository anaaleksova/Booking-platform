package com.bookingsaas.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResourceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotNull Boolean active,
        /* Empty means the resource can host any service of the business. */
        List<Long> serviceIds
) {
}
