package com.bookingsaas.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    Optional<Integration> findByBusinessIdAndProvider(Long businessId, IntegrationProvider provider);

    List<Integration> findByBusinessId(Long businessId);
}
