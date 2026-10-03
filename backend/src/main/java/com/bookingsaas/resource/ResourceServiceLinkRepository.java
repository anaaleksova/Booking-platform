package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ResourceServiceLinkRepository extends JpaRepository<ResourceServiceLink, Long> {

    List<ResourceServiceLink> findByResourceId(Long resourceId);

    List<ResourceServiceLink> findByResourceIdIn(Collection<Long> resourceIds);

    void deleteByResourceId(Long resourceId);

    /**
     * How many of the given ids are services of this business. Read straight
     * from the table so the resource module does not depend on the service
     * module (which itself depends on resource types).
     */
    @Query(value = "select count(*) from service where business_id = :businessId and id in (:ids)",
           nativeQuery = true)
    long countOwnedServices(@Param("businessId") Long businessId, @Param("ids") Collection<Long> ids);
}
