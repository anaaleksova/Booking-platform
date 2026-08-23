package com.bookingsaas.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Every tunable value the application owns. Nothing here is hardcoded in the
 * domain; all of it comes from configuration or environment variables.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        @NotBlank String frontendUrl,
        Notifications notifications
) {

    public record Jwt(
            /* HS256 requires >= 256 bits of key material. */
            @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters")
            String secret,
            @Min(1) long expirationMinutes,
            @NotBlank String issuer
    ) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Notifications(
            @NotBlank String fromAddress,
            boolean enabled,
            @Min(1000) long dispatchIntervalMs,
            @Min(1000) long reminderScanIntervalMs
    ) {
    }
}
