package com.bookingsaas.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByIdAndBusinessId(Long id, Long businessId);

    List<Staff> findByBusinessIdOrderByNameAsc(Long businessId);

    List<Staff> findByBusinessIdAndActiveTrueOrderByNameAsc(Long businessId);

    List<Staff> findByBusinessIdAndIdInOrderByNameAsc(Long businessId, Collection<Long> ids);

    /** Resolves the staff record behind a logged-in staff member. */
    Optional<Staff> findByBusinessIdAndUserId(Long businessId, Long userId);
}
