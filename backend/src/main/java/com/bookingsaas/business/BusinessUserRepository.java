package com.bookingsaas.business;

import com.bookingsaas.common.security.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {

    /** Login emails of the active owners, the longest-standing first. */
    @Query(value = """
            SELECT u.email FROM business_user bu JOIN app_user u ON u.id = bu.user_id
            WHERE bu.business_id = :businessId AND bu.role = 'BUSINESS_OWNER' AND u.active
            ORDER BY bu.id""", nativeQuery = true)
    List<String> findOwnerEmails(@Param("businessId") Long businessId);

    List<BusinessUser> findByUserId(Long userId);

    List<BusinessUser> findByBusinessId(Long businessId);

    Optional<BusinessUser> findByUserIdAndBusinessId(Long userId, Long businessId);

    boolean existsByBusinessIdAndRole(Long businessId, BusinessRole role);
}
