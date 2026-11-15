package com.bookingsaas.integration.meta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetaConnectionRepository extends JpaRepository<MetaConnection, Long> {

    Optional<MetaConnection> findByBusinessId(Long businessId);

    Optional<MetaConnection> findByPageId(String pageId);
}
