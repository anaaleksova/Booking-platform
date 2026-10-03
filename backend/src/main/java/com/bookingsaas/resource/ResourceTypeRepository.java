package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {

    Optional<ResourceType> findByIdAndBusinessId(Long id, Long businessId);

    List<ResourceType> findByBusinessIdOrderByNameAsc(Long businessId);

    List<ResourceType> findByBusinessIdAndIdIn(Long businessId, Collection<Long> ids);

    Optional<ResourceType> findByBusinessIdAndEmployeeTypeTrue(Long businessId);
}
