package com.bookingsaas.integration.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationTokenRepository extends JpaRepository<IntegrationToken, Long> {

    Optional<IntegrationToken> findByTokenHash(String tokenHash);

    List<IntegrationToken> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    Optional<IntegrationToken> findByIdAndBusinessId(Long id, Long businessId);
}
