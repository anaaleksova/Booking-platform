package com.bookingsaas.booking.dto;

import jakarta.validation.constraints.Size;

public record CancelBookingRequest(@Size(max = 255) String reason) {
}
