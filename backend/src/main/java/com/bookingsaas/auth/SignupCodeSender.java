package com.bookingsaas.auth;

/**
 * Delivers the sign-up verification code. An interface so tests can capture
 * the code instead of sending mail.
 */
public interface SignupCodeSender {

    /**
     * @throws RuntimeException when the message cannot be delivered; sign-up
     *                          then fails rather than leaving the person waiting
     *                          for a code that never comes
     */
    void send(String email, String fullName, String code);
}
