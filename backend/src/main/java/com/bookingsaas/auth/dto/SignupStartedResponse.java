package com.bookingsaas.auth.dto;

/**
 * The code is on its way.
 *
 * @param existingAccount the email already has an account (one made for
 *                        booking): confirming adds the business to it and
 *                        sets the password just entered
 */
public record SignupStartedResponse(boolean existingAccount) {
}
