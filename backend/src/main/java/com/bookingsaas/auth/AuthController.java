package com.bookingsaas.auth;

import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.auth.dto.CurrentUserResponse;
import com.bookingsaas.auth.dto.CustomerRegisterRequest;
import com.bookingsaas.auth.dto.ForgotPasswordRequest;
import com.bookingsaas.auth.dto.ResetPasswordRequest;
import com.bookingsaas.auth.dto.LoginRequest;
import com.bookingsaas.auth.dto.RegisterRequest;
import com.bookingsaas.auth.dto.ResendCodeRequest;
import com.bookingsaas.auth.dto.SignupStartedResponse;
import com.bookingsaas.auth.dto.VerifySignupRequest;
import com.bookingsaas.common.security.CurrentTenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up, sign-in and the session. Every endpoint returning an
 * {@link AuthResponse} also sets the session cookie (see {@link SessionCookieAdvice}).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentTenant currentTenant;
    private final SessionCookies sessionCookies;

    public AuthController(AuthService authService, CurrentTenant currentTenant, SessionCookies sessionCookies) {
        this.authService = authService;
        this.currentTenant = currentTenant;
        this.sessionCookies = sessionCookies;
    }

    /**
     * A business owner's sign-up, step one: emails a 6-digit code. The account
     * and the business are created by {@code /register/verify}; for an email
     * that already has a booking account, the business is added to that account.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SignupStartedResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.startOwnerSignup(request);
    }

    /** The emailed code creates the owner's account and business, and signs them in. */
    @PostMapping("/register/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse verifyOwnerSignup(@Valid @RequestBody VerifySignupRequest request) {
        return authService.verifySignup(request);
    }

    /**
     * Who is signed in, or 204 for nobody. Unlike {@code /me} it is not an
     * error to ask without a session, so the app can check on every start.
     */
    @GetMapping("/session")
    public ResponseEntity<CurrentUserResponse> session() {
        return currentTenant.optionalUser()
                .map(user -> ResponseEntity.ok(CurrentUserResponse.from(user)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Ends the browser session. The token is stateless, so this clears the cookie that holds it. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookies.clear()).build();
    }

    /** Emails a 6-digit code; calling again sends a new one. No account is created yet. */
    @PostMapping("/customer/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startCustomerSignup(@Valid @RequestBody CustomerRegisterRequest request) {
        authService.startCustomerSignup(request);
    }

    /** The emailed code creates the account and signs the customer in. */
    @PostMapping("/customer/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse verifyCustomerSignup(@Valid @RequestBody VerifySignupRequest request) {
        return authService.verifySignup(request);
    }

    /**
     * A new code for a sign-up (owner or customer) still waiting for one —
     * all the page needs after a reload, as the server kept the details.
     */
    @PostMapping("/signup/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendSignupCode(@Valid @RequestBody ResendCodeRequest request) {
        authService.resendSignupCode(request.email());
    }

    /** Emails a reset code if the account exists. Always 202, so it reveals nothing. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
    }

    /** The emailed code sets a new password and signs the user in. */
    @PostMapping("/password/reset")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(currentTenant.user());
    }

    @PostMapping("/switch-business/{businessId}")
    public AuthResponse switchBusiness(@PathVariable Long businessId) {
        return authService.switchBusiness(currentTenant.user(), businessId);
    }
}
