package com.bookingsaas.integration;

import com.bookingsaas.notification.ReservationNotificationEvent;
import com.bookingsaas.notification.StaffMember;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Employees who log in: the owner invites them, they accept through the
 * emailed link, and from then on they see only their own reservations.
 */
@DisplayName("staff access")
@RecordApplicationEvents
class StaffAccessIT extends AbstractSpaIntegrationTest {

    private static final String ELENA = "elena@spa.test";

    @Autowired
    ApplicationEvents events;

    private long elenaId;
    private long elenaResource;
    private long annaResource;
    private long employeeRequirement;

    @BeforeEach
    void addElena() throws Exception {
        JsonNode elena = send(post("/api/admin/employees"), owner, """
                {"name":"Elena","email":"%s","active":true,"capacity":1}""".formatted(ELENA), 201);
        elenaId = elena.get("id").asLong();
        elenaResource = elena.get("resourceId").asLong();
        for (JsonNode employee : send(get("/api/admin/employees"), owner, null, 200)) {
            if (employee.get("name").asText().equals("Anna")) {
                annaResource = employee.get("resourceId").asLong();
            }
        }
        long employeeType = employeeTypeId(owner);
        for (JsonNode requirement : send(get("/api/admin/services/" + classicId), owner, null, 200).get("requirements")) {
            if (requirement.get("resourceTypeId").asLong() == employeeType) {
                employeeRequirement = requirement.get("id").asLong();
            }
        }
    }

    @Test
    void an_invited_employee_sets_a_password_and_becomes_staff() throws Exception {
        JsonNode invited = send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        assertThat(invited.get("state").asText()).isEqualTo("INVITED");
        String token = token(ELENA);

        JsonNode info = send(get("/api/auth/invitations/" + token), null, null, 200);
        assertThat(info.get("businessName").asText()).isEqualTo("Serenity Test Spa");
        assertThat(info.get("employeeName").asText()).isEqualTo("Elena");
        assertThat(info.get("accountExists").asBoolean()).isFalse();

        JsonNode session = send(post("/api/auth/invitations/" + token + "/accept-new"), null, """
                {"fullName":"Elena Petrova","password":"password123"}""", 200);
        assertThat(session.get("user").get("activeBusiness").get("role").asText()).isEqualTo("STAFF");

        // The link is used up, and the owner sees the employee can log in.
        send(get("/api/auth/invitations/" + token), null, null, 410);
        assertThat(state(elenaId)).isEqualTo("ACTIVE");
        // They can sign in like anyone else.
        send(post("/api/auth/login"), null, """
                {"email":"elena@spa.test","password":"password123"}""", 200);
    }

    @Test
    void staff_see_and_complete_only_their_own_reservations_and_nothing_owner_only() throws Exception {
        String staff = acceptAsNew();
        long hers = reservationFor(elenaResource, "10:00");
        long annas = reservationFor(annaResource, "12:00");

        List<Long> visible = new ArrayList<>();
        send(get("/api/admin/reservations"), staff, null, 200).forEach(r -> visible.add(r.get("id").asLong()));
        assertThat(visible).containsExactly(hers);
        send(get("/api/admin/reservations/" + annas), staff, null, 403);
        send(get("/api/admin/business"), staff, null, 200);
        send(get("/api/admin/services"), staff, null, 200);

        send(patch("/api/admin/reservations/" + hers), staff, "{\"status\":\"COMPLETED\"}", 200);
        send(patch("/api/admin/reservations/" + annas), staff, "{\"status\":\"COMPLETED\"}", 403);

        // Cancelling, moving and creating stay with the owner, as does everything else.
        long another = reservationFor(elenaResource, "15:00");
        send(patch("/api/admin/reservations/" + another), staff, "{\"status\":\"CANCELLED\"}", 403);
        send(put("/api/admin/reservations/" + another + "/schedule"), staff,
                "{\"startTime\":\"%sT16:00:00\"}".formatted(day), 403);
        send(post("/api/admin/reservations"), staff, booking(elenaResource, "17:00"), 403);
        for (String path : List.of("/api/admin/customers", "/api/admin/analytics/reservations", "/api/admin/discounts",
                "/api/admin/business/settings", "/api/admin/integrations", "/api/admin/employees", "/api/admin/staff",
                "/api/admin/dashboard", "/api/admin/site")) {
            JsonNode denied = send(get(path), staff, null, 403);
            assertThat(denied.get("error").asText()).as(path).isEqualTo("ACCESS_DENIED");
        }
        send(post("/api/admin/employees/" + elenaId + "/invite"), staff, null, 403);
    }

    @Test
    void removing_access_takes_effect_on_the_next_request() throws Exception {
        String staff = acceptAsNew();
        send(get("/api/admin/reservations"), staff, null, 200);

        send(delete("/api/admin/employees/" + elenaId + "/access"), owner, null, 204);

        JsonNode denied = send(get("/api/admin/reservations"), staff, null, 403);
        assertThat(denied.get("error").asText()).isEqualTo("NO_BUSINESS_CONTEXT");
        assertThat(state(elenaId)).isEqualTo("NONE");
        // They can be invited again.
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
    }

    @Test
    void an_existing_account_accepts_by_signing_in_with_the_invited_address() throws Exception {
        String elenasAccount = customerToken(ELENA);
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        String token = token(ELENA);

        assertThat(send(get("/api/auth/invitations/" + token), null, null, 200).get("accountExists").asBoolean())
                .isTrue();
        send(post("/api/auth/invitations/" + token + "/accept-new"), null, """
                {"fullName":"Elena","password":"password123"}""", 409);
        send(post("/api/auth/invitations/" + token + "/accept"), customerToken("someone@else.test"), null, 403);
        send(post("/api/auth/invitations/" + token + "/accept"), null, null, 401);

        JsonNode session = send(post("/api/auth/invitations/" + token + "/accept"), elenasAccount, null, 200);
        assertThat(session.get("user").get("activeBusiness").get("role").asText()).isEqualTo("STAFF");
    }

    @Test
    void only_the_newest_link_works_and_a_withdrawn_one_never_does() throws Exception {
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        String first = token(ELENA);
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        String second = token(ELENA);
        assertThat(second).isNotEqualTo(first);
        send(get("/api/auth/invitations/" + first), null, null, 410);
        send(get("/api/auth/invitations/" + second), null, null, 200);

        send(delete("/api/admin/employees/" + elenaId + "/invite"), owner, null, 204);
        send(get("/api/auth/invitations/" + second), null, null, 410);
        assertThat(state(elenaId)).isEqualTo("NONE");
    }

    @Test
    void a_booking_for_a_staff_member_is_addressed_to_them_and_not_to_employees_without_a_login() throws Exception {
        acceptAsNew();
        events.clear();

        reservationFor(elenaResource, "10:00");
        reservationFor(annaResource, "12:00");   // Anna cannot log in: no staff email for her

        List<List<String>> recipients = events.stream(ReservationNotificationEvent.class)
                .map(event -> event.payload().staff().stream().map(StaffMember::email).toList())
                .toList();
        assertThat(recipients).containsExactly(List.of(ELENA), List.of());
    }

    @Test
    void reassigning_to_another_employee_at_the_same_time_tells_both() throws Exception {
        acceptAsNew();
        long id = reservationFor(elenaResource, "10:00");
        events.clear();

        send(put("/api/admin/reservations/" + id + "/schedule"), owner, """
                {"startTime":"%sT10:00:00","resourceSelections":[{"requirementId":%d,"resourceIds":[%d]}]}
                """.formatted(day, employeeRequirement, annaResource), 200);

        ReservationNotificationEvent event = events.stream(ReservationNotificationEvent.class).findFirst().orElseThrow();
        assertThat(event.kind()).isEqualTo(ReservationNotificationEvent.Kind.STAFF_CHANGED);
        assertThat(event.previousStaff()).extracting(StaffMember::email).containsExactly(ELENA);
        assertThat(event.payload().staff()).isEmpty();
    }

    @Test
    void an_employee_needs_an_email_before_an_invitation() throws Exception {
        long nameOnly = send(post("/api/admin/employees"), owner, """
                {"name":"Maria","active":true,"capacity":1}""", 201).get("id").asLong();
        send(post("/api/admin/employees/" + nameOnly + "/invite"), owner, null, 400);
    }

    @Test
    void deleting_an_employee_removes_their_login() throws Exception {
        String staff = acceptAsNew();
        send(delete("/api/admin/employees/" + elenaId), owner, null, 204);
        assertThat(send(get("/api/admin/reservations"), staff, null, 403).get("error").asText())
                .isEqualTo("NO_BUSINESS_CONTEXT");
    }

    // --- helpers -------------------------------------------------------------

    private String acceptAsNew() throws Exception {
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        send(post("/api/auth/invitations/" + token(ELENA) + "/accept-new"), null, """
                {"fullName":"Elena Petrova","password":"password123"}""", 200);
        return lastSessionToken();
    }

    private String token(String email) {
        String link = inviteLinks.get(email);
        assertThat(link).startsWith("http://localhost:5173/invite/");
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private String state(long employeeId) throws Exception {
        for (JsonNode access : send(get("/api/admin/staff"), owner, null, 200)) {
            if (access.get("employeeId").asLong() == employeeId) {
                return access.get("state").asText();
            }
        }
        throw new AssertionError("employee " + employeeId + " not listed");
    }

    private long reservationFor(long employeeResource, String time) throws Exception {
        return send(post("/api/admin/reservations"), owner, booking(employeeResource, time), 201).get("id").asLong();
    }

    private String booking(long employeeResource, String time) {
        return """
                {"serviceId":%d,"participants":1,"startTime":"%sT%s:00",
                 "resourceSelections":[{"requirementId":%d,"resourceIds":[%d]}],
                 "customer":{"firstName":"Ana","lastName":"Petrova","email":"ana@example.com","phone":"+38970111222"}}
                """.formatted(classicId, day, time, employeeRequirement, employeeResource);
    }
}
