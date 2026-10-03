package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every finder is scoped by {@code businessId}. */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Optional<Resource> findByIdAndBusinessId(Long id, Long businessId);

    List<Resource> findByBusinessIdOrderByNameAsc(Long businessId);

    List<Resource> findByBusinessIdAndIdInOrderByNameAsc(Long businessId, Collection<Long> ids);

    List<Resource> findByBusinessIdAndResourceTypeIdInAndActiveTrueOrderByNameAsc(Long businessId,
                                                                                  Collection<Long> typeIds);

    List<Resource> findByBusinessIdAndActiveTrueOrderByNameAsc(Long businessId);

    long countByResourceTypeId(Long resourceTypeId);
}
