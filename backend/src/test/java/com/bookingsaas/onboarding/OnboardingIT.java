package com.bookingsaas.onboarding;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The "Getting started" checklist follows what the owner does. */
class OnboardingIT extends AbstractIntegrationTest {

    @Test
    void a_new_business_starts_with_every_step_open() throws Exception {
        String token = registerAndLogin("owner@example.com", "Fresh Cuts");

        JsonNode onboarding = send(get("/api/admin/onboarding"), token, null, 200);
        assertThat(onboarding.get("businessType").asText()).isEqualTo("OTHER");
        assertThat(onboarding.get("slug").asText()).isEqualTo(slugOf(token));
        assertThat(onboarding.get("dismissed").asBoolean()).isFalse();
        assertThat(done(onboarding)).containsOnlyKeys("SERVICES", "RESOURCES", "HOURS", "CONTACT", "PHOTO", "SHARE")
                .doesNotContainValue(true);
    }

    @Test
    void saving_part_of_the_setup_ticks_its_step() throws Exception {
        String token = registerAndLogin("owner@example.com", "Fresh Cuts");

        send(post("/api/admin/employees"), token, """
                {"name":"Elena","active":true,"capacity":1,"serviceIds":[]}""", 201);
        send(put("/api/admin/opening-hours"), token, """
                [{"dayOfWeek":1,"startTime":"10:00","endTime":"18:00"}]""", 200);

        Map<String, Boolean> done = done(send(get("/api/admin/onboarding"), token, null, 200));
        assertThat(done).containsEntry("RESOURCES", true).containsEntry("HOURS", true)
                .containsEntry("SERVICES", false);
    }

    @Test
    void contact_and_photo_follow_the_business_details() throws Exception {
        String token = registerAndLogin("owner@example.com", "Fresh Cuts");
        JsonNode business = send(get("/api/admin/business"), token, null, 200);

        send(put("/api/admin/business"), token, """
                {"name":"Fresh Cuts","slug":"%s","phone":"+389 70 111 222","address":"Main street 1, Skopje",
                 "timezone":"Europe/Skopje","currency":"EUR"}""".formatted(business.get("slug").asText()), 200);

        Map<String, Boolean> done = done(send(get("/api/admin/onboarding"), token, null, 200));
        assertThat(done).containsEntry("CONTACT", true).containsEntry("PHOTO", false);
        send(post("/api/admin/onboarding/steps/CONTACT"), token, null, 400);   // not ticked by hand
    }

    @Test
    void looks_good_and_hiding() throws Exception {
        String token = registerAndLogin("owner@example.com", "Fresh Cuts");

        send(post("/api/admin/onboarding/steps/SERVICES"), token, null, 200);
        JsonNode shared = send(post("/api/admin/onboarding/steps/SHARE"), token, null, 200);
        assertThat(done(shared)).containsEntry("SERVICES", true).containsEntry("SHARE", true);

        assertThat(send(post("/api/admin/onboarding/dismiss"), token, null, 200).get("dismissed").asBoolean())
                .isTrue();
        assertThat(send(get("/api/admin/onboarding"), token, null, 200).get("dismissed").asBoolean()).isTrue();
    }

    @Test
    void a_business_from_before_templates_has_no_checklist() throws Exception {
        String token = registerAndLogin("owner@example.com", "Fresh Cuts");
        jdbcTemplate.update("delete from business_onboarding");

        send(get("/api/admin/onboarding"), token, null, 204);
        send(post("/api/admin/onboarding/steps/SERVICES"), token, null, 404);
        // Saving still works; there is just nothing to tick.
        send(put("/api/admin/opening-hours"), token, """
                [{"dayOfWeek":1,"startTime":"10:00","endTime":"18:00"}]""", 200);
    }

    @Test
    void staff_cannot_see_it() throws Exception {
        String owner = registerAndLogin("owner@example.com", "Fresh Cuts");
        long employeeId = send(post("/api/admin/employees"), owner, """
                {"name":"Elena","email":"elena@example.com","active":true,"capacity":1,"serviceIds":[]}""", 201)
                .get("id").asLong();
        send(post("/api/admin/employees/" + employeeId + "/invite"), owner, null, 200);
        String link = inviteLinks.get("elena@example.com");
        String inviteToken = link.substring(link.lastIndexOf('/') + 1);
        send(post("/api/auth/invitations/" + inviteToken + "/accept-new"), null, """
                {"fullName":"Elena","password":"password123"}""", 200);

        send(get("/api/admin/onboarding"), lastSessionToken(), null, 403);
    }

    private static Map<String, Boolean> done(JsonNode onboarding) {
        Map<String, Boolean> done = new HashMap<>();
        onboarding.get("steps").forEach(step -> done.put(step.get("key").asText(), step.get("done").asBoolean()));
        return done;
    }
}
