package com.bookingsaas.auth;

import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.auth.dto.CurrentUserResponse;
import com.bookingsaas.auth.dto.CustomerRegisterRequest;
import com.bookingsaas.auth.dto.ForgotPasswordRequest;
import com.bookingsaas.auth.dto.ResetPasswordRequest;
import com.bookingsaas.auth.dto.LoginRequest;
import com.bookingsaas.auth.dto.RegisterRequest;
import com.bookingsaas.auth.dto.VerifySignupRequest;
import com.bookingsaas.common.security.CurrentTenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout is intentionally absent: the token is stateless, so signing out is a
 * client-side discard of the token.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentTenant currentTenant;

    public AuthController(AuthService authService, CurrentTenant currentTenant) {
        this.authService = authService;
        this.currentTenant = currentTenant;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
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
        return authService.verifyCustomerSignup(request);
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
