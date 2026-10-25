package com.bookingsaas.auth;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A business owner's sign-up creates nothing until the emailed code proves the address. */
class OwnerSignupIT extends AbstractIntegrationTest {

    private static final String EMAIL = "new.owner@example.com";

    @Test
    void the_account_and_business_exist_only_after_the_code_is_confirmed() throws Exception {
        start(202);
        assertThat(sentCodes).containsKey(EMAIL);
        assertThat(jdbcTemplate.queryForObject("select count(*) from business", Long.class)).isZero();
        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(EMAIL), 401);

        JsonNode session = send(post("/api/auth/register/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(EMAIL, sentCodes.get(EMAIL)), 201);

        JsonNode business = session.get("user").get("activeBusiness");
        assertThat(business.get("businessName").asText()).isEqualTo("Fresh Cuts");
        assertThat(business.get("role").asText()).isEqualTo("BUSINESS_OWNER");
        JsonNode admin = send(get("/api/admin/business"), lastSessionToken(), null, 200);
        assertThat(admin.get("timezone").asText()).isEqualTo("Europe/Skopje");
    }

    @Test
    void a_wrong_code_creates_nothing() throws Exception {
        start(202);
        String wrong = sentCodes.get(EMAIL).equals("000000") ? "111111" : "000000";

        send(post("/api/auth/register/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(EMAIL, wrong), 400);
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user", Long.class)).isZero();
    }

    @Test
    void a_registered_email_cannot_register_a_business_again() throws Exception {
        registerAndLogin(EMAIL, "First Business");
        assertThat(start(409).get("error").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void someone_who_booked_before_signs_up_with_the_same_email_and_a_new_password() throws Exception {
        customerToken(EMAIL);   // a booking account, password "password123"

        assertThat(start("new-password-1", 202).get("existingAccount").asBoolean()).isTrue();
        JsonNode session = send(post("/api/auth/register/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(EMAIL, sentCodes.get(EMAIL)), 201);

        assertThat(session.get("user").get("activeBusiness").get("role").asText()).isEqualTo("BUSINESS_OWNER");
        assertThat(send(get("/api/admin/business"), lastSessionToken(), null, 200).get("name").asText())
                .isEqualTo("Fresh Cuts");
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user", Long.class)).isOne();
        // The password entered at sign-up is the account's now; the old one no longer works.
        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(EMAIL), 401);
        JsonNode login = send(post("/api/auth/login"), null, """
                {"email":"%s","password":"new-password-1"}""".formatted(EMAIL), 200);
        assertThat(login.get("user").get("activeBusiness").get("businessName").asText()).isEqualTo("Fresh Cuts");
    }

    @Test
    void a_page_that_lost_the_form_can_have_the_code_sent_again_with_just_the_email() throws Exception {
        start(202);
        String first = sentCodes.get(EMAIL);
        send(post("/api/auth/signup/resend"), null, "{\"email\":\"%s\"}".formatted(EMAIL), 429);   // cool-down

        jdbcTemplate.update("update pending_signup set sent_at = sent_at - interval '2 minutes'");
        send(post("/api/auth/signup/resend"), null, "{\"email\":\"%s\"}".formatted(EMAIL), 202);
        String second = sentCodes.get(EMAIL);
        if (!second.equals(first)) {
            send(post("/api/auth/register/verify"), null, """
                    {"email":"%s","code":"%s"}""".formatted(EMAIL, first), 400);   // the old code is replaced
        }
        JsonNode session = send(post("/api/auth/register/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(EMAIL, second), 201);
        assertThat(session.get("user").get("activeBusiness").get("businessName").asText()).isEqualTo("Fresh Cuts");
    }

    @Test
    void there_is_nothing_to_resend_without_a_sign_up() throws Exception {
        send(post("/api/auth/signup/resend"), null, "{\"email\":\"%s\"}".formatted(EMAIL), 410);
    }

    @Test
    void a_new_email_is_not_an_existing_account() throws Exception {
        assertThat(start(202).get("existingAccount").asBoolean()).isFalse();
    }

    @Test
    void without_the_code_an_existing_account_is_untouched() throws Exception {
        customerToken(EMAIL);
        start("new-password-1", 202);

        send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(EMAIL), 200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from business", Long.class)).isZero();
    }

    private JsonNode start(int expectedStatus) throws Exception {
        return start("password123", expectedStatus);
    }

    private JsonNode start(String password, int expectedStatus) throws Exception {
        return send(post("/api/auth/register"), null, """
                {"fullName":"New Owner","email":"%s","password":"%s",
                 "businessName":"Fresh Cuts","timezone":"Europe/Skopje"}""".formatted(EMAIL, password), expectedStatus);
    }
}
