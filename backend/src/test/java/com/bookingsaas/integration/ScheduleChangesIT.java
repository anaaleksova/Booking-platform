package com.bookingsaas.integration;

import com.bookingsaas.availability.StaffTimeOffEntered;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Time off, closures and special opening hours against bookings already made:
 * the clashes are reported, never moved silently, and staff manage only their
 * own time off.
 */
@DisplayName("schedule changes")
class ScheduleChangesIT extends AbstractSpaIntegrationTest {

    private static final String ELENA = "elena@spa.test";

    private long elenaId;
    private long elenaResource;
    private long employeeRequirement;

    @BeforeEach
    void addElena() throws Exception {
        JsonNode elena = send(post("/api/admin/employees"), owner, """
                {"name":"Elena","email":"%s","active":true,"capacity":1}""".formatted(ELENA), 201);
        elenaId = elena.get("id").asLong();
        elenaResource = elena.get("resourceId").asLong();
        long employeeType = employeeTypeId(owner);
        for (JsonNode requirement : send(get("/api/admin/services/" + classicId), owner, null, 200).get("requirements")) {
            if (requirement.get("resourceTypeId").asLong() == employeeType) {
                employeeRequirement = requirement.get("id").asLong();
            }
        }
    }

    @Test
    void blocking_an_employee_reports_their_bookings_and_reassigning_clears_the_clash() throws Exception {
        long booked = bookWithElena("10:00");
        String from = "%sT09:00:00".formatted(day);
        String to = "%sT13:00:00".formatted(day);

        JsonNode preview = send(get("/api/admin/blocked-times/conflicts?resourceId=%d&startTime=%s&endTime=%s"
                .formatted(elenaResource, from, to)), owner, null, 200);
        assertThat(ids(preview)).containsExactly(booked);
        assertThat(preview.get(0).get("resources").toString()).contains("Elena");

        JsonNode block = send(post("/api/admin/blocked-times"), owner, """
                {"resourceId":%d,"startTime":"%s","endTime":"%s","reason":"Doctor"}"""
                .formatted(elenaResource, from, to), 201);
        assertThat(ids(block.get("conflicts"))).containsExactly(booked);
        assertThat(block.get("enteredByStaff").asBoolean()).isFalse();

        // Same time, no selection: the blocked employee is swapped for a free one.
        JsonNode moved = send(put("/api/admin/reservations/" + booked + "/schedule"), owner,
                "{\"startTime\":\"%sT10:00:00\"}".formatted(day), 200);
        assertThat(moved.toString()).doesNotContain("Elena").contains("Anna");
        assertThat(blocks(owner).get(0).get("conflicts")).isEmpty();
    }

    @Test
    void a_closure_reports_every_booking_in_it_until_each_is_cancelled() throws Exception {
        long morning = bookWithElena("10:00");
        long afternoon = bookWithElena("16:00");

        JsonNode closure = send(post("/api/admin/blocked-times"), owner, """
                {"startTime":"%sT08:00:00","endTime":"%sT12:00:00","reason":"Plumber"}""".formatted(day, day), 201);
        assertThat(ids(closure.get("conflicts"))).containsExactly(morning).doesNotContain(afternoon);

        send(patch("/api/admin/reservations/" + morning), owner, "{\"status\":\"CANCELLED\",\"reason\":\"Closed\"}", 200);
        assertThat(blocks(owner).get(0).get("conflicts")).isEmpty();
    }

    @Test
    void special_hours_replace_the_week_and_report_bookings_left_outside() throws Exception {
        long early = bookWithElena("10:00");
        long late = bookWithElena("15:00");

        String shortDay = """
                {"hours":[{"startTime":"09:00","endTime":"13:00"}],"note":"Christmas Eve"}""";
        assertThat(ids(send(post("/api/admin/special-hours/%s/conflicts".formatted(day)), owner, shortDay, 200)))
                .containsExactly(late);
        JsonNode saved = send(put("/api/admin/special-hours/" + day), owner, shortDay, 200);
        assertThat(saved.get("closed").asBoolean()).isFalse();
        assertThat(ids(saved.get("conflicts"))).containsExactly(late).doesNotContain(early);

        // New bookings follow the special hours: 09:00 to 12:00 starts only.
        JsonNode slots = send(get("/api/admin/availability?serviceId=%d&from=%s".formatted(classicId, day)),
                owner, null, 200).get(0).get("slots");
        assertThat(slots.get(0).get("time").asText()).startsWith("09:00");
        assertThat(slots.get(slots.size() - 1).get("time").asText()).startsWith("12:00");

        JsonNode page = send(get("/api/public/businesses/" + slug()), null, null, 200);
        assertThat(page.get("specialHours").get(0).get("note").asText()).isEqualTo("Christmas Eve");

        // Closed all day, then back to the week.
        JsonNode closed = send(put("/api/admin/special-hours/" + day), owner, "{\"hours\":[]}", 200);
        assertThat(closed.get("closed").asBoolean()).isTrue();
        assertThat(ids(closed.get("conflicts"))).containsExactlyInAnyOrder(early, late);
        send(delete("/api/admin/special-hours/" + day), owner, null, 204);
        assertThat(send(get("/api/admin/special-hours"), owner, null, 200)).isEmpty();
    }

    @Test
    void staff_enter_their_own_time_off_and_the_owner_is_told_about_their_bookings() throws Exception {
        String staff = acceptInvitation();
        long hers = bookWithElena("10:00");
        long annaResource = otherEmployeeResource();

        JsonNode me = send(get("/api/admin/staff/me"), staff, null, 200);
        assertThat(me.get("resourceId").asLong()).isEqualTo(elenaResource);

        JsonNode timeOff = send(post("/api/admin/blocked-times"), staff, """
                {"resourceId":%d,"startTime":"%sT09:00:00","endTime":"%sT12:00:00","reason":"Dentist"}"""
                .formatted(elenaResource, day, day), 201);
        assertThat(timeOff.get("enteredByStaff").asBoolean()).isTrue();
        assertThat(ids(timeOff.get("conflicts"))).containsExactly(hers);

        ArgumentCaptor<StaffTimeOffEntered> notice = ArgumentCaptor.forClass(StaffTimeOffEntered.class);
        verify(timeOffNoticeSender).send(eq("owner@spa.test"), notice.capture());
        assertThat(notice.getValue().employeeName()).isEqualTo("Elena");
        assertThat(notice.getValue().conflicts()).extracting(c -> c.reservationId()).containsExactly(hers);

        // Their own calendar only; closures stay with the owner.
        send(post("/api/admin/blocked-times"), staff, """
                {"resourceId":%d,"startTime":"%sT09:00:00","endTime":"%sT10:00:00"}"""
                .formatted(annaResource, day, day), 403);
        send(post("/api/admin/blocked-times"), staff, """
                {"startTime":"%sT09:00:00","endTime":"%sT10:00:00"}""".formatted(day, day), 403);
        send(get("/api/admin/special-hours"), staff, null, 403);

        // They see their time off and the closures, not other people's time off.
        send(post("/api/admin/blocked-times"), owner, """
                {"resourceId":%d,"startTime":"%sT14:00:00","endTime":"%sT15:00:00"}"""
                .formatted(annaResource, day, day), 201);
        send(post("/api/admin/blocked-times"), owner, """
                {"startTime":"%sT19:00:00","endTime":"%sT20:00:00"}""".formatted(day, day), 201);
        JsonNode visible = blocks(staff);
        assertThat(visible).hasSize(2);
        for (JsonNode block : visible) {
            assertThat(!block.hasNonNull("resourceId") || block.get("resourceId").asLong() == elenaResource).isTrue();
        }

        send(delete("/api/admin/blocked-times/" + timeOff.get("id").asLong()), staff, null, 204);
    }

    @Test
    void the_owner_entering_time_off_sends_no_email() throws Exception {
        send(post("/api/admin/blocked-times"), owner, """
                {"resourceId":%d,"startTime":"%sT09:00:00","endTime":"%sT12:00:00"}"""
                .formatted(elenaResource, day, day), 201);
        verify(timeOffNoticeSender, never()).send(anyString(), any());
    }

    // --- helpers -------------------------------------------------------------

    private long bookWithElena(String time) throws Exception {
        return send(post("/api/admin/reservations"), owner, """
                {"serviceId":%d,"participants":1,"startTime":"%sT%s:00",
                 "resourceSelections":[{"requirementId":%d,"resourceIds":[%d]}],
                 "customer":{"firstName":"Ana","lastName":"Petrova","email":"ana@example.com","phone":"+38970111222"}}
                """.formatted(classicId, day, time, employeeRequirement, elenaResource), 201).get("id").asLong();
    }

    private JsonNode blocks(String session) throws Exception {
        return send(get("/api/admin/blocked-times"), session, null, 200);
    }

    private String acceptInvitation() throws Exception {
        send(post("/api/admin/employees/" + elenaId + "/invite"), owner, null, 200);
        String link = inviteLinks.get(ELENA);
        send(post("/api/auth/invitations/" + link.substring(link.lastIndexOf('/') + 1) + "/accept-new"), null, """
                {"fullName":"Elena Petrova","password":"password123"}""", 200);
        return lastSessionToken();
    }

    private long otherEmployeeResource() throws Exception {
        for (JsonNode employee : send(get("/api/admin/employees"), owner, null, 200)) {
            if (employee.get("name").asText().equals("Anna")) {
                return employee.get("resourceId").asLong();
            }
        }
        throw new AssertionError("Anna not found");
    }

    private static List<Long> ids(JsonNode conflicts) {
        List<Long> ids = new ArrayList<>();
        conflicts.forEach(conflict -> ids.add(conflict.get("reservationId").asLong()));
        return ids;
    }
}
