package com.bookingsaas.business;

import com.bookingsaas.common.security.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {

    List<BusinessUser> findByUserId(Long userId);

    List<BusinessUser> findByBusinessId(Long businessId);

    Optional<BusinessUser> findByUserIdAndBusinessId(Long userId, Long businessId);

    boolean existsByBusinessIdAndRole(Long businessId, BusinessRole role);
}
