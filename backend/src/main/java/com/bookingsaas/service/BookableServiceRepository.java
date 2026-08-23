package com.bookingsaas.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code businessId}. Loading by id alone is not
 * offered, so no caller can accidentally reach another tenant's row.
 */
public interface BookableServiceRepository extends JpaRepository<BookableService, Long> {

    Optional<BookableService> findByIdAndBusinessId(Long id, Long businessId);

    List<BookableService> findByBusinessIdOrderByNameAsc(Long businessId);

    List<BookableService> findByBusinessIdAndActiveTrueOrderByNameAsc(Long businessId);

    List<BookableService> findByBusinessIdAndIdIn(Long businessId, List<Long> ids);
}
