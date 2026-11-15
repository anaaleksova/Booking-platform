package com.bookingsaas.integration.meta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface MetaConnectSessionRepository extends JpaRepository<MetaConnectSession, UUID> {

    @Modifying
    @Query("delete from MetaConnectSession s where s.expiresAt < :now")
    void deleteExpired(Instant now);

    @Modifying
    @Query("delete from MetaConnectSession s where s.businessId = :businessId")
    void deleteByBusinessId(Long businessId);
}
