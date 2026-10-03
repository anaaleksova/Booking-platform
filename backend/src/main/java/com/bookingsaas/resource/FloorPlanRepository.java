package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FloorPlanRepository extends JpaRepository<FloorPlan, Long> {

    @EntityGraph(attributePaths = "items")
    List<FloorPlan> findByBusinessIdAndResourceTypeIdOrderByPositionAscIdAsc(Long businessId, Long resourceTypeId);

    @EntityGraph(attributePaths = "items")
    List<FloorPlan> findByBusinessIdAndResourceTypeIdInOrderByPositionAscIdAsc(Long businessId,
                                                                              Collection<Long> resourceTypeIds);

    List<FloorPlan> findByBusinessId(Long businessId);
}
