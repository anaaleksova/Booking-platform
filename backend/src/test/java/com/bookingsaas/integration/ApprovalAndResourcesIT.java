package com.bookingsaas.integration;

import com.bookingsaas.notification.ReservationNotificationEvent;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Bookings the owner approves first, bookings left on a deactivated resource,
 * and setting up many resources at once.
 */
@DisplayName("approval and resources")
@RecordApplicationEvents
class ApprovalAndResourcesIT extends AbstractSpaIntegrationTest {

    @Autowired
    ApplicationEvents events;

    private long hallType;
    private long groupId;

    @BeforeEach
    void groupService() throws Exception {
        hallType = send(post("/api/admin/resource-types"), owner, """
                {"name":"Hall","active":true}""", 201).get("id").asLong();
        for (String hall : List.of("Hall 1", "Hall 2")) {
            send(post("/api/admin/resources"), owner, """
                    {"resourceTypeId":%d,"name":"%s","capacity":10,"active":true}""".formatted(hallType, hall), 201);
        }
        // Up to four guests; groups of three or more need the owner's approval.
        groupId = send(post("/api/admin/services"), owner, """
                {"name":"Group Session","durationMinutes":60,"price":100,"pricePerParticipant":false,"maxParticipants":4,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":false,"active":true,
                 "approvalRequired":true,"approvalFromParticipants":3,
                 "requirements":[{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false}]}
                """.formatted(hallType), 201).get("id").asLong();
    }

    @Test
    void large_groups_become_requests_that_hold_the_slot_until_the_owner_decides() throws Exception {
        JsonNode service = send(get("/api/public/businesses/" + slug() + "/services/" + groupId), null, null, 200);
        assertThat(service.get("approvalRequired").asBoolean()).isTrue();
        assertThat(service.get("approvalFromParticipants").asInt()).isEqualTo(3);

        JsonNode small = book(2, "10:00");
        JsonNode large = book(3, "12:00");
        assertThat(small.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(large.get("status").asText()).isEqualTo("PENDING");
        assertThat(kinds()).containsExactly(ReservationNotificationEvent.Kind.CREATED,
                ReservationNotificationEvent.Kind.REQUESTED);

        // The request holds its hall like any booking.
        long requestId = reservationId(large);
        assertThat(send(get("/api/admin/reservations/" + requestId), owner, null, 200).get("resources")).hasSize(1);

        JsonNode approved = send(patch("/api/admin/reservations/" + requestId), owner, "{\"status\":\"CONFIRMED\"}", 200);
        assertThat(approved.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(kinds()).endsWith(ReservationNotificationEvent.Kind.APPROVED);

        // A second request, declined: the customer is told, and the slot is free again.
        long declined = reservationId(book(4, "15:00"));
        JsonNode cancelled = send(patch("/api/admin/reservations/" + declined), owner,
                "{\"status\":\"CANCELLED\",\"reason\":\"Fully booked for groups that day\"}", 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(kinds()).endsWith(ReservationNotificationEvent.Kind.DECLINED);
        ReservationNotificationEvent last = events.stream(ReservationNotificationEvent.class).reduce((a, b) -> b)
                .orElseThrow();
        assertThat(last.payload().detail()).isEqualTo("Fully booked for groups that day");
        assertThat(last.payload().staff()).isEmpty();
    }

    @Test
    void chat_bookings_follow_the_same_approval_rule() throws Exception {
        JsonNode created = integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "group-ig-1"), token,
                reservationBody(groupId, 3, "11:00", "2001"), 201);
        assertThat(created.toString()).contains("\"PENDING\"");
    }

    @Test
    void the_business_taking_a_large_group_itself_confirms_it_at_once() throws Exception {
        JsonNode booked = send(post("/api/admin/reservations"), owner, """
                {"serviceId":%d,"participants":4,"startTime":"%sT10:00:00",
                 "customer":{"firstName":"Ana","lastName":"Petrova","email":"ana@example.com","phone":"+38970111222"}}
                """.formatted(groupId, day), 201);
        assertThat(booked.get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void a_deactivated_resources_bookings_are_listed_and_moved_to_free_ones() throws Exception {
        long room1 = roomNamed("Hall 1");
        long room2 = roomNamed("Hall 2");
        // Two bookings at 10:00 fill both halls; the one at 14:00 has a hall to spare.
        List<Long> onRoom1 = new ArrayList<>();
        for (String time : List.of("10:00", "14:00")) {
            onRoom1.add(adminBooking(time, room1));
        }
        adminBooking("10:00", room2);

        send(patch("/api/admin/resources/" + room1 + "/active?value=false"), owner, null, 200);
        assertThat(ids(send(get("/api/admin/resources/" + room1 + "/upcoming-reservations"), owner, null, 200)))
                .containsExactlyElementsOf(onRoom1);
        JsonNode stranded = send(get("/api/admin/resources/stranded-reservations"), owner, null, 200);
        assertThat(stranded).hasSize(1);
        assertThat(stranded.get(0).get("resourceId").asLong()).isEqualTo(room1);
        assertThat(stranded.get(0).get("reservations").asLong()).isEqualTo(2);

        JsonNode result = send(post("/api/admin/resources/" + room1 + "/reassign-reservations"), owner, null, 200);
        assertThat(ids(result.get("reassigned"))).containsExactly(onRoom1.get(1));
        assertThat(result.get("reassigned").get(0).get("resources").toString()).contains("Hall 2");
        assertThat(result.get("failed")).hasSize(1);
        assertThat(result.get("failed").get(0).get("booking").get("reservationId").asLong()).isEqualTo(onRoom1.get(0));
        assertThat(result.get("failed").get(0).get("reason").asText()).isNotBlank();

        // An active resource keeps its bookings: there is nothing to move them off.
        send(post("/api/admin/resources/" + room2 + "/reassign-reservations"), owner, null, 400);
    }

    @Test
    void numbered_resources_are_created_at_once_skipping_names_already_taken() throws Exception {
        long tableType = send(post("/api/admin/resource-types"), owner, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        send(post("/api/admin/resources"), owner, """
                {"resourceTypeId":%d,"name":"Table 2","capacity":2,"active":true}""".formatted(tableType), 201);

        JsonNode result = send(post("/api/admin/resources/bulk"), owner, """
                {"resourceTypeId":%d,"namePrefix":"Table","firstNumber":1,"lastNumber":5,"capacity":4,"active":true,
                 "hours":[{"dayOfWeek":1,"startTime":"12:00","endTime":"22:00"}]}""".formatted(tableType), 201);
        assertThat(result.get("created")).hasSize(4);
        assertThat(result.get("skipped").get(0).asText()).isEqualTo("Table 2");
        assertThat(result.get("created").get(0).get("capacity").asInt()).isEqualTo(4);
        assertThat(result.get("created").get(0).get("hours")).hasSize(1);

        send(post("/api/admin/resources/bulk"), owner, """
                {"resourceTypeId":%d,"namePrefix":"Seat","firstNumber":1,"lastNumber":101,"active":true}"""
                .formatted(tableType), 400);
    }

    @Test
    void a_schedule_is_copied_to_resources_of_the_same_type_only() throws Exception {
        long room1 = roomNamed("Room 1");
        long room2 = roomNamed("Room 2");
        send(put("/api/admin/resources/" + room1 + "/hours"), owner, """
                [{"dayOfWeek":1,"startTime":"09:00","endTime":"13:00"},
                 {"dayOfWeek":3,"startTime":"14:00","endTime":"18:00"}]""", 200);

        JsonNode changed = send(post("/api/admin/resources/" + room1 + "/copy-schedule"), owner,
                "{\"targetIds\":[%d]}".formatted(room2), 200);
        assertThat(changed.get(0).asLong()).isEqualTo(room2);
        assertThat(send(get("/api/admin/resources/" + room2), owner, null, 200).get("hours")).hasSize(2);

        long annaResource = send(get("/api/admin/employees"), owner, null, 200).get(0).get("resourceId").asLong();
        send(post("/api/admin/resources/" + room1 + "/copy-schedule"), owner,
                "{\"targetIds\":[%d]}".formatted(annaResource), 400);
    }

    // --- helpers -------------------------------------------------------------

    private JsonNode book(int participants, String time) throws Exception {
        return send(post("/api/public/businesses/" + slug() + "/reservations"), customerToken("group@example.com"), """
                {"serviceId":%d,"participants":%d,"startTime":"%sT%s:00",
                 "customer":{"firstName":"Gala","lastName":"Group","phone":"+38970999888"}}
                """.formatted(groupId, participants, day, time), 201);
    }

    private long reservationId(JsonNode publicReservation) throws Exception {
        String reference = publicReservation.get("reference").asText();
        for (JsonNode reservation : send(get("/api/admin/reservations?from=" + day + "&to=" + day), owner, null, 200)) {
            if (reservation.get("reference").asText().equals(reference)) {
                return reservation.get("id").asLong();
            }
        }
        throw new AssertionError("reservation " + reference + " not found");
    }

    private long adminBooking(String time, long room) throws Exception {
        long roomRequirement = send(get("/api/admin/services/" + groupId), owner, null, 200).get("requirements")
                .get(0).get("id").asLong();
        return send(post("/api/admin/reservations"), owner, """
                {"serviceId":%d,"participants":1,"startTime":"%sT%s:00",
                 "resourceSelections":[{"requirementId":%d,"resourceIds":[%d]}],
                 "customer":{"firstName":"Ana","lastName":"Petrova","email":"ana@example.com","phone":"+38970111222"}}
                """.formatted(groupId, day, time, roomRequirement, room), 201).get("id").asLong();
    }

    private long roomNamed(String name) throws Exception {
        for (JsonNode resource : send(get("/api/admin/resources"), owner, null, 200)) {
            if (resource.get("name").asText().equals(name)) {
                return resource.get("id").asLong();
            }
        }
        throw new AssertionError(name + " not found");
    }

    private List<ReservationNotificationEvent.Kind> kinds() {
        return events.stream(ReservationNotificationEvent.class).map(ReservationNotificationEvent::kind).toList();
    }

    private static List<Long> ids(JsonNode bookings) {
        List<Long> ids = new ArrayList<>();
        bookings.forEach(booking -> ids.add(booking.get("reservationId").asLong()));
        return ids;
    }
}
