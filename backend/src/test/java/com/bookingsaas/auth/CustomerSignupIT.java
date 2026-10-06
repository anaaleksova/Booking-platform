package com.bookingsaas.auth;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Customer sign-up only creates an account once the emailed code is confirmed. */
class CustomerSignupIT extends AbstractIntegrationTest {

    private static final String EMAIL = "new.customer@example.com";

    @Test
    void no_account_exists_until_the_code_is_confirmed() throws Exception {
        start(EMAIL, 202);
        assertThat(sentCodes).containsKey(EMAIL);

        // Signing in before verifying fails: there is no account yet.
        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(EMAIL), 401);

        JsonNode session = verify(EMAIL, sentCodes.get(EMAIL), 201);
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.get("user").hasNonNull("activeBusiness")).isFalse();

        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(EMAIL), 200);
    }

    @Test
    void a_wrong_code_is_rejected_and_attempts_run_out() throws Exception {
        start(EMAIL, 202);
        String wrong = sentCodes.get(EMAIL).equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(verify(EMAIL, wrong, 400).get("error").asText()).isEqualTo("INVALID_VERIFICATION_CODE");
        }
        assertThat(verify(EMAIL, wrong, 410).get("error").asText()).isEqualTo("VERIFICATION_EXPIRED");
        // Even the right code no longer works: a new one has to be requested.
        assertThat(verify(EMAIL, sentCodes.get(EMAIL), 410).get("error").asText()).isEqualTo("VERIFICATION_EXPIRED");
    }

    @Test
    void a_new_code_cannot_be_requested_straight_away() throws Exception {
        start(EMAIL, 202);
        assertThat(start(EMAIL, 429).get("error").asText()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void a_registered_email_cannot_sign_up_again() throws Exception {
        customerToken(EMAIL);
        assertThat(start(EMAIL, 409).get("error").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void verifying_an_unknown_email_fails() throws Exception {
        assertThat(verify("nobody@example.com", "123456", 410).get("error").asText())
                .isEqualTo("VERIFICATION_EXPIRED");
    }

    private JsonNode start(String email, int expectedStatus) throws Exception {
        return send(post("/api/auth/customer/register"), null, """
                {"fullName":"New Customer","email":"%s","password":"password123"}""".formatted(email),
                expectedStatus);
    }

    private JsonNode verify(String email, String code, int expectedStatus) throws Exception {
        return send(post("/api/auth/customer/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(email, code), expectedStatus);
    }
}
