package com.bookingsaas.auth;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Forgotten password: an emailed code sets a new password and ends old sessions. */
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String EMAIL = "forgetful@example.com";

    @Test
    void the_emailed_code_sets_a_new_password_and_signs_in() throws Exception {
        String oldSession = customerToken(EMAIL);
        forgot(EMAIL);
        assertThat(resetCodes).containsKey(EMAIL);

        JsonNode session = reset(EMAIL, resetCodes.get(EMAIL), "brand-new-pass", 200);
        assertThat(session.get("accessToken").asText()).isNotBlank();

        login(EMAIL, "password123", 401);
        login(EMAIL, "brand-new-pass", 200);
        // The new session works; one from before the change does not.
        send(get("/api/auth/me"), session.get("accessToken").asText(), null, 200);
        assertThat(send(get("/api/auth/me"), oldSession, null, 401).get("error").asText()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void business_owners_can_reset_too() throws Exception {
        registerAndLogin("owner@example.com", "Owner Co");
        forgot("owner@example.com");
        JsonNode session = reset("owner@example.com", resetCodes.get("owner@example.com"), "another-pass", 200);
        assertThat(session.get("user").get("activeBusiness").get("businessName").asText()).isEqualTo("Owner Co");
    }

    @Test
    void an_unknown_email_gets_the_same_answer_and_no_email() throws Exception {
        forgot("nobody@example.com");
        assertThat(resetCodes).isEmpty();
        assertThat(reset("nobody@example.com", "123456", "whatever-pass", 410).get("error").asText())
                .isEqualTo("VERIFICATION_EXPIRED");
    }

    @Test
    void wrong_codes_run_out_and_the_password_stays() throws Exception {
        customerToken(EMAIL);
        forgot(EMAIL);
        String wrong = resetCodes.get(EMAIL).equals("000000") ? "111111" : "000000";
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(reset(EMAIL, wrong, "brand-new-pass", 400).get("error").asText())
                    .isEqualTo("INVALID_VERIFICATION_CODE");
        }
        reset(EMAIL, wrong, "brand-new-pass", 410);
        reset(EMAIL, resetCodes.get(EMAIL), "brand-new-pass", 410);
        login(EMAIL, "password123", 200);
    }

    @Test
    void a_short_new_password_is_rejected() throws Exception {
        customerToken(EMAIL);
        forgot(EMAIL);
        JsonNode error = reset(EMAIL, resetCodes.get(EMAIL), "short", 400);
        assertThat(error.get("fieldErrors").has("newPassword")).isTrue();
    }

    private void forgot(String email) throws Exception {
        send(post("/api/auth/password/forgot"), null, """
                {"email":"%s"}""".formatted(email), 202);
    }

    private JsonNode reset(String email, String code, String password, int expectedStatus) throws Exception {
        return send(post("/api/auth/password/reset"), null, """
                {"email":"%s","code":"%s","newPassword":"%s"}""".formatted(email, code, password), expectedStatus);
    }

    private void login(String email, String password, int expectedStatus) throws Exception {
        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"%s"}""".formatted(email, password), expectedStatus);
    }
}
