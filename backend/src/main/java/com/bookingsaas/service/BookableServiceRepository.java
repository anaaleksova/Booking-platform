package com.bookingsaas.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Read from the table directly so this module does not depend on the reservation module. */
    @Query(value = "select exists (select 1 from reservation where service_id = :serviceId)", nativeQuery = true)
    boolean hasReservations(@Param("serviceId") Long serviceId);
}
