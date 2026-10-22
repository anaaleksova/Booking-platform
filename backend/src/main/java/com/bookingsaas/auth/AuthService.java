package com.bookingsaas.auth;

import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.auth.dto.CurrentUserResponse;
import com.bookingsaas.auth.dto.CustomerRegisterRequest;
import com.bookingsaas.auth.dto.ForgotPasswordRequest;
import com.bookingsaas.auth.dto.ResetPasswordRequest;
import com.bookingsaas.auth.dto.LoginRequest;
import com.bookingsaas.auth.dto.RegisterRequest;
import com.bookingsaas.auth.dto.VerifySignupRequest;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.ratelimit.RateLimiter;
import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.BusinessMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_CODE_ATTEMPTS = 5;
    /** With five guesses per code, at most 25 guesses an hour at any one account's code. */
    private static final int MAX_CODES_PER_HOUR = 5;
    private static final int MAX_LOGIN_FAILURES = 10;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final String CODES_SENT = "codes-sent:";
    private static final String LOGIN_FAILURES = "login-failures:";

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;
    private final PendingSignupRepository pendingSignups;
    private final SignupCodeSender signupCodeSender;
    private final PasswordResetRepository passwordResets;
    private final PasswordResetCodeSender passwordResetCodeSender;
    private final Clock clock;
    private final RateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();
    /** Checked against when the email is unknown, so that case costs the same BCrypt work. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       BusinessService businessService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       PrincipalLoader principalLoader,
                       PendingSignupRepository pendingSignups,
                       SignupCodeSender signupCodeSender,
                       PasswordResetRepository passwordResets,
                       PasswordResetCodeSender passwordResetCodeSender,
                       Clock clock,
                       RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = passwordEncoder.encode("no-such-account-" + random.nextLong());
        this.pendingSignups = pendingSignups;
        this.signupCodeSender = signupCodeSender;
        this.passwordResets = passwordResets;
        this.passwordResetCodeSender = passwordResetCodeSender;
        this.clock = clock;
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
    }

    /**
     * Step one of a business owner's sign-up. Like a customer's, it only
     * emails a code: the account and the business are created by
     * {@link #verifySignup} once the code proves the address is theirs. The
     * owner's address receives customer details and calendar invitations, so
     * it has to be real.
     */
    @Transactional
    public void startOwnerSignup(RegisterRequest request) {
        startSignup(request.email(), request.fullName(), request.password(),
                request.businessName().trim(), request.timezone());
    }

    /**
     * Step one of a customer sign-up: remember the details and email a code.
     * No account exists until {@link #verifySignup} proves the email is
     * real and theirs. Asking again replaces the pending sign-up and its code.
     */
    @Transactional
    public void startCustomerSignup(CustomerRegisterRequest request) {
        startSignup(request.email(), request.fullName(), request.password(), null, null);
    }

    private void startSignup(String rawEmail, String fullName, String password, String businessName, String timezone) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists. Sign in instead.");
        }
        Instant now = clock.instant();
        PendingSignup pending = pendingSignups.findById(email).orElseGet(PendingSignup::new);
        if (pending.getSentAt() != null && pending.getSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw ApiException.of(ErrorCode.TOO_MANY_REQUESTS,
                    "A code was just sent. Please wait a minute before asking for another.");
        }
        if (!rateLimiter.tryAcquire(CODES_SENT + email, MAX_CODES_PER_HOUR, Duration.ofHours(1))) {
            throw ApiException.of(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many codes were sent to this address. Please try again in an hour.");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        pending.setEmail(email);
        pending.setFullName(fullName.trim());
        pending.setPasswordHash(passwordEncoder.encode(password));
        pending.setCodeHash(sha256(code));
        pending.setAttempts(0);
        pending.setSentAt(now);
        pending.setExpiresAt(now.plus(CODE_LIFETIME));
        pending.setBusinessName(businessName);
        pending.setTimezone(timezone);
        pendingSignups.save(pending);

        try {
            signupCodeSender.send(email, pending.getFullName(), code);
        } catch (RuntimeException ex) {
            log.warn("Could not send sign-up code: {}", ex.getMessage());
            throw ApiException.of(ErrorCode.EMAIL_DELIVERY_FAILED,
                    "We could not send an email to this address. Check it and try again.");
        }
    }

    /**
     * Step two: the right code creates the account and signs the person in.
     * A customer gets a booking account (no business, so it can book but never
     * reach the admin API); an owner gets the account plus their business.
     */
    @Transactional(noRollbackFor = ApiException.class)   // a wrong guess must still count
    public AuthResponse verifySignup(VerifySignupRequest request) {
        String email = normalizeEmail(request.email());
        PendingSignup pending = pendingSignups.findById(email)
                .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
                .filter(candidate -> candidate.getAttempts() < MAX_CODE_ATTEMPTS)
                .orElseThrow(() -> ApiException.of(ErrorCode.VERIFICATION_EXPIRED,
                        "This code has expired. Ask for a new one."));

        if (!MessageDigest.isEqual(sha256(request.code().trim()).getBytes(StandardCharsets.UTF_8),
                pending.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingSignups.save(pending);
            int left = MAX_CODE_ATTEMPTS - pending.getAttempts();
            throw left > 0
                    ? ApiException.of(ErrorCode.INVALID_VERIFICATION_CODE,
                            "That code is not right. " + left + (left == 1 ? " try" : " tries") + " left.")
                    : ApiException.of(ErrorCode.VERIFICATION_EXPIRED,
                            "Too many wrong codes. Ask for a new one.");
        }

        if (userRepository.existsByEmail(email)) {
            pendingSignups.delete(pending);
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists. Sign in instead.");
        }
        User user = userRepository.save(new User(email, pending.getPasswordHash(), pending.getFullName()));
        pendingSignups.delete(pending);
        if (pending.getBusinessName() == null) {
            log.info("Registered customer account id={} (email verified)", user.getId());
            return issueToken(principalLoader.loadByUserId(user.getId(), null));
        }
        Business business = businessService.createBusiness(user.getId(), pending.getBusinessName(),
                pending.getTimezone());
        log.info("Registered user id={} with business id={} (email verified)", user.getId(), business.getId());
        return issueToken(principalLoader.loadByUserId(user.getId(), business.getId()));
    }

    /**
     * Forgotten password, step one: email a 6-digit code to the account.
     *
     * <p>Answers the same whether or not the account exists (and stays silent
     * during the resend cool-down), so the form cannot be used to find out who
     * has an account. A delivery failure is only logged for the same reason.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        PasswordReset reset = passwordResets.findById(user.getId()).orElseGet(PasswordReset::new);
        if (reset.getSentAt() != null && reset.getSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return;
        }
        // Each code allows a few guesses; capping codes per hour caps the guesses.
        if (!rateLimiter.tryAcquire(CODES_SENT + email, MAX_CODES_PER_HOUR, Duration.ofHours(1))) {
            log.info("Password reset code not sent for user id={}: hourly limit reached", user.getId());
            return;
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        reset.setUserId(user.getId());
        reset.setCodeHash(sha256(code));
        reset.setAttempts(0);
        reset.setSentAt(now);
        reset.setExpiresAt(now.plus(CODE_LIFETIME));
        passwordResets.save(reset);
        try {
            passwordResetCodeSender.send(user.getEmail(), user.getFullName(), code);
        } catch (RuntimeException ex) {
            log.warn("Could not send password reset code for user id={}: {}", user.getId(), ex.getMessage());
        }
    }

    /**
     * Step two: the right code sets the new password and signs the person in.
     * Every token issued before the change stops working (see {@link JwtService}),
     * so whoever else may have been signed in is signed out.
     */
    @Transactional(noRollbackFor = ApiException.class)   // a wrong guess must still count
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
        PasswordReset reset = user == null ? null : passwordResets.findById(user.getId())
                .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
                .filter(candidate -> candidate.getAttempts() < MAX_CODE_ATTEMPTS)
                .orElse(null);
        if (reset == null) {
            throw ApiException.of(ErrorCode.VERIFICATION_EXPIRED, "This code has expired. Ask for a new one.");
        }

        if (!MessageDigest.isEqual(sha256(request.code().trim()).getBytes(StandardCharsets.UTF_8),
                reset.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            reset.setAttempts(reset.getAttempts() + 1);
            passwordResets.save(reset);
            int left = MAX_CODE_ATTEMPTS - reset.getAttempts();
            throw left > 0
                    ? ApiException.of(ErrorCode.INVALID_VERIFICATION_CODE,
                            "That code is not right. " + left + (left == 1 ? " try" : " tries") + " left.")
                    : ApiException.of(ErrorCode.VERIFICATION_EXPIRED,
                            "Too many wrong codes. Ask for a new one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        passwordResets.delete(reset);
        log.info("Password reset for user id={}", user.getId());
        return issueToken(principalLoader.loadByUserId(user.getId(), null));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String failuresKey = LOGIN_FAILURES + email;
        if (rateLimiter.isExhausted(failuresKey, MAX_LOGIN_FAILURES, LOGIN_FAILURE_WINDOW)) {
            throw ApiException.of(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many failed sign-ins. Please wait 15 minutes or reset your password.");
        }
        // Identical response, and the same BCrypt work, for an unknown email
        // and a wrong password: neither the message nor the timing tells
        // which accounts exist.
        User user = userRepository.findByEmail(email).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(),
                user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches) {
            rateLimiter.hit(failuresKey, LOGIN_FAILURE_WINDOW);
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password.");
        }
        rateLimiter.reset(failuresKey);

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

    /** Whether a login account exists for this email. */
    @Transactional(readOnly = true)
    public boolean accountExists(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    /**
     * An account whose email is already proven — by the invitation link that
     * reached it — so no code is needed. Returns the new user's id.
     */
    @Transactional
    public Long createVerifiedAccount(String email, String fullName, String password) {
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists. Sign in instead.");
        }
        User user = userRepository.save(new User(normalized, passwordEncoder.encode(password), fullName.trim()));
        log.info("Created account id={} from an invitation", user.getId());
        return user.getId();
    }

    /** A session for the user, with this business active. */
    @Transactional(readOnly = true)
    public AuthResponse sessionFor(Long userId, Long businessId) {
        return issueToken(principalLoader.loadByUserId(userId, businessId));
    }

    private AuthResponse issueToken(AuthenticatedUser principal) {
        Long businessId = principal.activeMembership().map(BusinessMembership::businessId).orElse(null);
        String token = jwtService.issue(principal.userId(), principal.email(), businessId, principal.getPassword());
        return AuthResponse.of(token, Instant.now().plus(jwtService.expiration()),
                CurrentUserResponse.from(principal));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
