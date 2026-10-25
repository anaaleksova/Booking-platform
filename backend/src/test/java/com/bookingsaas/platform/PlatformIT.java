package com.bookingsaas.platform;

import com.bookingsaas.auth.PlatformAdminAccounts;
import com.bookingsaas.support.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The platform administrator (platform.admin@example.com in the test
 * configuration): sees every business and account, can enter any business
 * with owner rights, and take businesses and accounts offline — all logged.
 * Nobody else reaches any of it.
 */
@DisplayName("platform administration")
class PlatformIT extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "platform.admin@example.com";

    @Autowired
    private PlatformAdminAccounts platformAdminAccounts;

    private String owner;
    private long businessId;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndLogin("owner@bistro.test", "Lake Bistro");
        JsonNode business = send(get("/api/admin/business"), owner, null, 200);
        businessId = business.get("id").asLong();
        slug = business.get("slug").asText();
    }

    @Test
    void only_a_platform_administrator_reaches_the_platform_api() throws Exception {
        send(get("/api/platform/overview"), null, null, 401);
        send(get("/api/platform/overview"), owner, null, 403);
        send(get("/api/platform/businesses"), customerToken("guest@example.com"), null, 403);
        send(post("/api/platform/businesses/" + businessId + "/enter"), owner, null, 403);

        JsonNode me = send(get("/api/auth/me"), admin(), null, 200);
        assertThat(me.get("platformAdmin").asBoolean()).isTrue();
        assertThat(send(get("/api/auth/me"), owner, null, 200).get("platformAdmin").asBoolean()).isFalse();
    }

    @Test
    void sees_every_business_and_account() throws Exception {
        registerAndLogin("owner@spa.test", "Calm Spa");
        String admin = admin();

        JsonNode overview = send(get("/api/platform/overview"), admin, null, 200);
        assertThat(overview.get("businesses").asLong()).isEqualTo(2);
        assertThat(overview.get("accounts").asLong()).isEqualTo(3);

        JsonNode found = send(get("/api/platform/businesses?query=bistro"), admin, null, 200);
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).get("ownerEmails").get(0).asText()).isEqualTo("owner@bistro.test");

        JsonNode accounts = send(get("/api/platform/accounts?query=owner@bistro"), admin, null, 200);
        assertThat(accounts.get(0).get("roles").get(0).asText()).startsWith("Owner").endsWith("Lake Bistro");
    }

    @Test
    void enters_any_business_with_owner_rights_and_leaves_again() throws Exception {
        String admin = admin();

        JsonNode session = send(post("/api/platform/businesses/" + businessId + "/enter"), admin, null, 200);
        assertThat(session.get("user").get("activeBusiness").get("businessName").asText()).isEqualTo("Lake Bistro");
        assertThat(session.get("user").get("platformView").asBoolean()).isTrue();
        String inside = lastSessionToken();

        // The whole business admin, owner-only parts included.
        assertThat(send(get("/api/admin/business"), inside, null, 200).get("name").asText()).isEqualTo("Lake Bistro");
        send(get("/api/admin/events"), inside, null, 200);
        send(post("/api/admin/discounts"), inside, """
                {"name":"Welcome","code":"WELCOME","type":"PERCENTAGE","value":10,"active":true,
                 "appliesToAllServices":true,"serviceIds":[]}""", 201);

        assertThat(actions()).containsExactly("ENTERED_BUSINESS");

        JsonNode back = send(post("/api/platform/exit"), inside, null, 200);
        assertThat(back.get("user").has("activeBusiness")).isFalse();
        send(get("/api/admin/business"), lastSessionToken(), null, 403);
    }

    @Test
    void a_deactivated_business_is_offline_for_customers_its_owner_and_its_integrations() throws Exception {
        String token = send(post("/api/admin/integrations/tokens"), owner, """
                {"name":"n8n","permissions":["READ_SERVICES"]}""", 201).get("secret").asText();
        send(get("/api/integrations/businesses/" + businessId + "/services"), token, null, 200);
        String admin = admin();

        send(put("/api/platform/businesses/" + businessId + "/active"), admin, "{\"active\":false}", 200);

        send(get("/api/public/businesses/" + slug), null, null, 404);
        send(get("/api/admin/business"), owner, null, 403);
        send(get("/api/integrations/businesses/" + businessId + "/services"), token, null, 401);
        // The administrator can still look inside.
        send(post("/api/platform/businesses/" + businessId + "/enter"), admin, null, 200);
        send(get("/api/admin/business"), lastSessionToken(), null, 200);

        send(put("/api/platform/businesses/" + businessId + "/active"), admin, "{\"active\":true}", 200);
        send(get("/api/public/businesses/" + slug), null, null, 200);
        send(get("/api/admin/business"), owner, null, 200);
        assertThat(actions()).containsExactly("BUSINESS_REACTIVATED", "ENTERED_BUSINESS", "BUSINESS_DEACTIVATED");
    }

    @Test
    void a_deactivated_account_cannot_sign_in_but_an_administrator_cannot_be_deactivated() throws Exception {
        String admin = admin();
        long ownerId = send(get("/api/auth/me"), owner, null, 200).get("id").asLong();
        long adminId = send(get("/api/auth/me"), admin, null, 200).get("id").asLong();

        send(put("/api/platform/accounts/" + ownerId + "/active"), admin, "{\"active\":false}", 204);
        send(get("/api/admin/business"), owner, null, 403);   // "This account has been deactivated."
        send(post("/api/auth/login"), null, """
                {"email":"owner@bistro.test","password":"password123"}""", 403);

        send(put("/api/platform/accounts/" + adminId + "/active"), admin, "{\"active\":false}", 403);
        assertThat(actions()).containsExactly("ACCOUNT_DEACTIVATED");
    }

    @Test
    void a_configured_administrator_without_an_account_gets_one_whose_password_they_set_themselves() throws Exception {
        platformAdminAccounts.run(null);
        platformAdminAccounts.run(null);   // once only

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app_user where email = ?", Long.class, ADMIN_EMAIL)).isOne();
        // Nobody knows the generated password: "Forgot password?" is how the administrator gets in.
        send(post("/api/auth/password/forgot"), null, "{\"email\":\"%s\"}".formatted(ADMIN_EMAIL), 202);
        JsonNode session = send(post("/api/auth/password/reset"), null, """
                {"email":"%s","code":"%s","newPassword":"my-own-password"}
                """.formatted(ADMIN_EMAIL, resetCodes.get(ADMIN_EMAIL)), 200);
        assertThat(session.get("user").get("platformAdmin").asBoolean()).isTrue();
    }

    // --- helpers -----------------------------------------------------------

    /** The platform administrator signs up like anyone (here as a booking account) and is one by configuration. */
    private String admin() throws Exception {
        return customerToken(ADMIN_EMAIL);
    }

    private List<String> actions() throws Exception {
        List<String> actions = new ArrayList<>();
        send(get("/api/platform/activity"), admin(), null, 200).forEach(row -> actions.add(row.get("action").asText()));
        return actions;
    }
}
