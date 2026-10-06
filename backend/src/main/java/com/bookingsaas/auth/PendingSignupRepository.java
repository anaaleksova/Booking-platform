package com.bookingsaas.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingSignupRepository extends JpaRepository<PendingSignup, String> {
}
