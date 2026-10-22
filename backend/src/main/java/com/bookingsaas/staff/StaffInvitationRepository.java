package com.bookingsaas.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    Optional<StaffInvitation> findByEmployeeId(Long employeeId);

    List<StaffInvitation> findByBusinessId(Long businessId);

    void deleteByEmployeeId(Long employeeId);
}
