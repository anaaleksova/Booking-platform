package com.bookingsaas.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResourceHourRepository extends JpaRepository<ResourceHour, Long> {

    List<ResourceHour> findByResourceIdOrderByDayOfWeekAscStartTimeAsc(Long resourceId);

    List<ResourceHour> findByBusinessIdAndResourceIdIn(Long businessId, Collection<Long> resourceIds);

    void deleteByResourceId(Long resourceId);
}
