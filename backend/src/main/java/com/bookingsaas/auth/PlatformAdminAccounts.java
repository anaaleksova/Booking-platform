package com.bookingsaas.auth;

import com.bookingsaas.common.security.PlatformAdmins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Makes sure every configured platform administrator has an account, so a
 * fresh installation can be run from day one.
 *
 * <p>A created account gets a random password nobody knows: the administrator
 * sets their own with "Forgot password?", which proves the email is theirs.
 * No password ever sits in configuration or in the repository.
 */
@Component
public class PlatformAdminAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminAccounts.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformAdmins platformAdmins;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminAccounts(PlatformAdmins platformAdmins, UserRepository userRepository,
                                 PasswordEncoder passwordEncoder) {
        this.platformAdmins = platformAdmins;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : platformAdmins.emails()) {
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            User user = userRepository.save(new User(email,
                    passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)), "Platform administrator"));
            log.info("Created the platform administrator account id={}; its password is set with \"Forgot password?\"",
                    user.getId());
        }
    }
}
