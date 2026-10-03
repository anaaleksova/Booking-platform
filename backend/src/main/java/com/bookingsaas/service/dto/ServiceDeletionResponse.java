package com.bookingsaas.service.dto;

/** Outcome of a delete request: removed outright, or deactivated because history references it. */
public record ServiceDeletionResponse(boolean deleted, boolean deactivated, String message) {
}
