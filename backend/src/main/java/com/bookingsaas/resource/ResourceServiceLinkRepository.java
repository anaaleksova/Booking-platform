package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ResourceServiceLinkRepository extends JpaRepository<ResourceServiceLink, Long> {

    List<ResourceServiceLink> findByResourceId(Long resourceId);

    List<ResourceServiceLink> findByResourceIdIn(Collection<Long> resourceIds);

    void deleteByResourceId(Long resourceId);

    @Query("select l.resourceId from ResourceServiceLink l where l.serviceId = :serviceId")
    List<Long> findResourceIdsByServiceId(Long serviceId);
}
