package com.bookingsaas.auth;

/**
 * Delivers the password-reset code. An interface so tests can capture the code
 * instead of sending mail.
 */
public interface PasswordResetCodeSender {

    void send(String email, String fullName, String code);
}
