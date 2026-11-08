package com.bookingsaas.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndBusinessId(Long id, Long businessId);

    List<Employee> findByBusinessIdOrderByNameAsc(Long businessId);

    /** Resolves the employee record behind a logged-in STAFF user. */
    Optional<Employee> findByBusinessIdAndUserId(Long businessId, Long userId);

    List<Employee> findByBusinessIdAndResourceIdIn(Long businessId, Collection<Long> resourceIds);

    List<Employee> findByBusinessIdAndIdIn(Long businessId, Collection<Long> ids);
}
