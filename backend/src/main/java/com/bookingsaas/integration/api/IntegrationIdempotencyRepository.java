package com.bookingsaas.integration.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationIdempotencyRepository extends JpaRepository<IntegrationIdempotency, Long> {

    Optional<IntegrationIdempotency> findByBusinessIdAndOperationAndIdempotencyKey(Long businessId, String operation,
                                                                                   String idempotencyKey);
}
