package com.bookingsaas.auth;

import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.auth.dto.CurrentUserResponse;
import com.bookingsaas.auth.dto.LoginRequest;
import com.bookingsaas.auth.dto.RegisterRequest;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.BusinessMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Registration and login.
 *
 * <p>Passwords are BCrypt-hashed on the way in and never read back out; no DTO
 * in this module exposes {@code passwordHash}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;

    public AuthService(UserRepository userRepository,
                       BusinessService businessService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       PrincipalLoader principalLoader) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists.");
        }

        User user = userRepository.save(
                new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
        Business business = businessService.createBusiness(user.getId(), request.businessName(), request.timezone());

        log.info("Registered user id={} with business id={}", user.getId(), business.getId());
        return issueToken(principalLoader.loadByUserId(user.getId(), business.getId()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        // Identical response for unknown email and wrong password, so the
        // endpoint cannot be used to enumerate registered accounts.
        User user = userRepository.findByEmail(email)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password."));

        if (!user.isActive()) {
            throw ApiException.of(ErrorCode.ACCESS_DENIED, "This account has been deactivated.");
        }

        AuthenticatedUser principal = principalLoader.loadByUserId(user.getId(), null);
        log.info("Login succeeded for user id={}", user.getId());
        return issueToken(principal);
    }

    /** Re-issues a token bound to another business the user belongs to. */
    @Transactional(readOnly = true)
    public AuthResponse switchBusiness(AuthenticatedUser current, Long businessId) {
        BusinessMembership membership = current.membershipFor(businessId)
                .orElseThrow(() -> ApiException.accessDenied("You do not belong to this business."));
        return issueToken(current.withActiveMembership(membership));
    }

    private AuthResponse issueToken(AuthenticatedUser principal) {
        Long businessId = principal.activeMembership().map(BusinessMembership::businessId).orElse(null);
        String token = jwtService.issue(principal.userId(), principal.email(), businessId);
        return AuthResponse.of(token, Instant.now().plus(jwtService.expiration()),
                CurrentUserResponse.from(principal));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
