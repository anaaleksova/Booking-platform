package com.bookingsaas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The phone a customer types when booking is kept on their account for the next booking form. */
@DisplayName("account phone")
class AccountPhoneIT extends AbstractSpaIntegrationTest {

    @Test
    void a_new_account_has_no_phone_until_it_books() throws Exception {
        String customer = customerToken("mila@example.com");
        assertThat(session(customer).hasNonNull("phone")).isFalse();

        book(customer, "15:00", "+38970111222");

        assertThat(session(customer).get("phone").asText()).isEqualTo("+38970111222");
    }

    @Test
    void the_latest_phone_typed_is_the_one_kept() throws Exception {
        String customer = customerToken("mila@example.com");
        book(customer, "15:00", "+38970111222");
        book(customer, "17:00", "+38970999888");

        assertThat(session(customer).get("phone").asText()).isEqualTo("+38970999888");
        // Signing in again brings it along too.
        JsonNode login = send(post("/api/auth/login"), null, """
                {"email":"mila@example.com","password":"password123"}""", 200);
        assertThat(login.get("user").get("phone").asText()).isEqualTo("+38970999888");
    }

    private void book(String customer, String time, String phone) throws Exception {
        send(post("/api/public/businesses/" + slug() + "/reservations"), customer, """
                {"serviceId":%d,"participants":1,"startTime":"%sT%s:00",
                 "customer":{"firstName":"Mila","lastName":"Novak","phone":"%s"}}
                """.formatted(classicId, day, time, phone), 201);
    }

    private JsonNode session(String token) throws Exception {
        return send(get("/api/auth/session"), token, null, 200);
    }
}
