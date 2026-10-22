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

    private JsonNode start(int expectedStatus) throws Exception {
        return send(post("/api/auth/register"), null, """
                {"fullName":"New Owner","email":"%s","password":"password123",
                 "businessName":"Fresh Cuts","timezone":"Europe/Skopje"}""".formatted(EMAIL), expectedStatus);
    }
}
