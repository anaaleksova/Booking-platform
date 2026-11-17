package com.bookingsaas.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The phone a customer types when booking, kept on their account so the next
 * booking form, at any business, is filled in for them.
 */
@Service
public class AccountPhones {

    private final UserRepository userRepository;

    public AccountPhones(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Keeps {@code phone} as the account's latest; a blank one changes nothing. */
    @Transactional
    public void remember(Long userId, String phone) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        String latest = phone.trim();
        userRepository.findById(userId)
                .filter(user -> !latest.equals(user.getPhone()))
                .ifPresent(user -> user.setPhone(latest));
    }
}
