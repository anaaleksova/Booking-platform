package com.bookingsaas.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface StaffServiceLinkRepository extends JpaRepository<StaffServiceLink, Long> {

    List<StaffServiceLink> findByStaffId(Long staffId);

    List<StaffServiceLink> findByServiceId(Long serviceId);

    List<StaffServiceLink> findByStaffIdIn(Collection<Long> staffIds);

    void deleteByStaffId(Long staffId);

    @Query("select l.staffId from StaffServiceLink l where l.serviceId = :serviceId")
    List<Long> findStaffIdsByServiceId(Long serviceId);
}
