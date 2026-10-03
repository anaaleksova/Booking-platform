package com.bookingsaas.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceResourceRequirementRepository extends JpaRepository<ServiceResourceRequirement, Long> {

    List<ServiceResourceRequirement> findByServiceIdOrderByPositionAscIdAsc(Long serviceId);

    List<ServiceResourceRequirement> findByServiceIdInOrderByPositionAscIdAsc(Collection<Long> serviceIds);

    void deleteByServiceId(Long serviceId);
}
