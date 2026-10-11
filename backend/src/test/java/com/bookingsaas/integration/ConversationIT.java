package com.bookingsaas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Chat conversations as n8n drives them: each test plays the language model's
 * part by sending already-interpreted messages, and checks what the platform
 * asks next — and that nothing is booked without an explicit "yes" to a
 * summary the customer has seen.
 */
@DisplayName("conversations")
class ConversationIT extends AbstractSpaIntegrationTest {

    private static final String USER = "ig-chat-1";

    @Test
    void a_booking_conversation_from_first_message_to_reservation() throws Exception {
        JsonNode context = integration(get(api("/conversations/current?provider=INSTAGRAM&externalUserId=" + USER)),
                token, null, 200);
        assertThat(context.has("conversationId")).isFalse();
        assertThat(context.get("today").asText()).isEqualTo(day.minusDays(1).toString());

        JsonNode reply = turn("""
                {"intent":"BOOK","service":"massage"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("SERVICE");
        assertThat(codes(reply)).containsExactly("SERVICE_AMBIGUOUS");
        int couples = optionNumber(reply, "Couples Massage");

        reply = turn("""
                {"optionNumber":%d}""".formatted(couples));
        assertThat(reply.get("ask").asText()).isEqualTo("PARTICIPANTS");
        assertThat(reply.get("summary").get("maxParticipants").asInt()).isEqualTo(2);

        reply = turn("""
                {"participants":2}""");
        assertThat(reply.get("ask").asText()).isEqualTo("DATE");

        reply = turn("""
                {"date":"%s"}""".formatted(day));
        assertThat(reply.get("ask").asText()).isEqualTo("TIME");
        assertThat(reply.get("options").size()).isBetween(1, 10);

        reply = turn("""
                {"time":"17:30"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("CUSTOMER_INFO");
        assertThat(texts(reply.get("missingFields"))).containsExactly("firstName", "phone");

        reply = turn("""
                {"firstName":"Ana","phone":"+389 70 555 666"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("CONFIRMATION");
        JsonNode summary = reply.get("summary");
        assertThat(summary.get("serviceName").asText()).isEqualTo("Couples Massage");
        assertThat(summary.get("start").asText()).isEqualTo("17:30");
        assertThat(summary.get("price").get("finalPrice").decimalValue()).isEqualByComparingTo("80");
        assertThat(count("select count(*) from reservation")).isZero();

        reply = turn("""
                {"confirmation":"YES"}""");
        assertThat(reply.get("nextAction").asText()).isEqualTo("CREATE_RESERVATION");
        JsonNode action = reply.get("action");

        JsonNode reservation = integration(post(api("/reservations"))
                        .header("Idempotency-Key", action.get("idempotencyKey").asText()),
                token, action.get("reservation").toString(), 201);
        assertThat(reservation.get("source").asText()).isEqualTo("INSTAGRAM");
        assertThat(reservation.get("customerName").asText()).isEqualTo("Ana");
        assertThat(reservation.get("start").asText()).isEqualTo("17:30");

        // The chat is over; the platform remembers the conversation it came from.
        context = integration(get(api("/conversations/current?provider=INSTAGRAM&externalUserId=" + USER)),
                token, null, 200);
        assertThat(context.has("conversationId")).isFalse();
        assertThat(context.get("customer").get("known").asBoolean()).isTrue();
        assertThat(count("select count(*) from integration_conversation where state = 'COMPLETED' "
                + "and reservation_id is not null")).isEqualTo(1);
    }

    @Test
    void mentioning_a_time_never_books_without_a_confirmed_summary() throws Exception {
        // Everything in one message, including a "yes" — but no summary was shown yet.
        JsonNode reply = turn("""
                {"intent":"BOOK","service":"couples massage","participants":2,"date":"%s","time":"10:00",
                 "firstName":"Ana","phone":"+38970555666","confirmation":"YES"}""".formatted(day));

        assertThat(reply.get("nextAction").asText()).isEqualTo("REPLY");
        assertThat(reply.get("ask").asText()).isEqualTo("CONFIRMATION");

        // A change at the confirmation step asks again rather than booking the changed version.
        reply = turn("""
                {"time":"11:00","confirmation":"YES"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("CONFIRMATION");
        assertThat(reply.get("summary").get("start").asText()).isEqualTo("11:00");

        reply = turn("""
                {"confirmation":"NO"}""");
        assertThat(reply.get("outcome").asText()).isEqualTo("BOOKING_ABANDONED");
        assertThat(count("select count(*) from reservation")).isZero();
    }

    @Test
    void a_known_customer_is_not_asked_for_details_again_and_can_cancel_in_chat() throws Exception {
        book("14:00");

        JsonNode reply = turn("""
                {"intent":"BOOK","service":"classic massage","date":"%s","time":"09:00"}""".formatted(day));
        assertThat(reply.get("ask").asText()).isEqualTo("CONFIRMATION");
        assertThat(reply.get("customer").get("firstName").asText()).isEqualTo("Ana");
        turn("""
                {"abort":true}""");

        reply = turn("""
                {"intent":"CANCEL"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("CANCEL_CONFIRMATION");
        assertThat(reply.get("reservation").get("start").asText()).isEqualTo("14:00");

        reply = turn("""
                {"confirmation":"YES"}""");
        assertThat(reply.get("nextAction").asText()).isEqualTo("CANCEL_RESERVATION");
        long reservationId = reply.get("action").get("reservationId").asLong();

        JsonNode cancelled = integration(post(api("/reservations/" + reservationId + "/cancel")), token, """
                {"conversationId":%d}""".formatted(reply.get("conversationId").asLong()), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        reply = turn("""
                {"intent":"CANCEL"}""");
        assertThat(reply.get("outcome").asText()).isEqualTo("NO_UPCOMING_RESERVATIONS");
    }

    @Test
    void someone_else_taking_the_slot_leads_back_to_fresh_times() throws Exception {
        toConfirmation("11:00");
        JsonNode reply = turn("""
                {"confirmation":"YES"}""");
        JsonNode action = reply.get("action");

        // Another couple books 11:00 between the summary and the "yes".
        integration(post(api("/reservations")).header("Idempotency-Key", "someone-else-1"), token,
                reservationBody(couplesId, 2, "11:00", "9999"), 201);
        JsonNode conflict = integration(post(api("/reservations"))
                        .header("Idempotency-Key", action.get("idempotencyKey").asText()),
                token, action.get("reservation").toString(), 409);

        reply = integration(post(api("/conversations/" + reply.get("conversationId").asLong() + "/action-failed")),
                token, """
                        {"errorCode":"%s","message":"taken"}""".formatted(conflict.get("error").asText()), 200);
        assertThat(reply.get("ask").asText()).isEqualTo("TIME");
        assertThat(codes(reply)).contains("SLOT_JUST_TAKEN");
        assertThat(optionValues(reply)).isNotEmpty().doesNotContain("11:00", "10:30", "11:30");
    }

    @Test
    void unavailable_answers_are_explained_and_alternatives_offered() throws Exception {
        JsonNode reply = turn("""
                {"intent":"BOOK","service":"couples massage","participants":3}""");
        assertThat(codes(reply)).contains("INVALID_PARTICIPANTS");
        assertThat(reply.get("ask").asText()).isEqualTo("PARTICIPANTS");

        reply = turn("""
                {"participants":2,"date":"%s","time":"06:00"}""".formatted(day));
        assertThat(codes(reply)).contains("TIME_NOT_AVAILABLE");
        assertThat(reply.get("ask").asText()).isEqualTo("TIME");
        assertThat(optionValues(reply)).contains("08:00");

        reply = turn("""
                {"date":"%s"}""".formatted(day.minusDays(3)));
        assertThat(codes(reply)).contains("DATE_IN_PAST");
        assertThat(reply.get("ask").asText()).isEqualTo("DATE");

        reply = turn("""
                {"intent":"BOOK","service":"hot stone therapy"}""");
        assertThat(codes(reply)).contains("SERVICE_NOT_FOUND");
        assertThat(reply.get("options").size()).isEqualTo(2);
    }

    @Test
    void a_question_in_between_keeps_the_booking_where_it_was() throws Exception {
        turn("""
                {"intent":"BOOK","service":"couples massage","participants":2}""");

        JsonNode reply = turn("""
                {"intent":"QUESTION"}""");
        assertThat(reply.get("ask").asText()).isEqualTo("DATE");
        assertThat(reply.get("business").get("name").asText()).isEqualTo("Serenity Test Spa");
        assertThat(reply.get("business").get("services").size()).isEqualTo(2);
    }

    // --- helpers -------------------------------------------------------------

    private void book(String time) throws Exception {
        toConfirmation(time);
        JsonNode action = turn("""
                {"confirmation":"YES"}""").get("action");
        integration(post(api("/reservations")).header("Idempotency-Key", action.get("idempotencyKey").asText()),
                token, action.get("reservation").toString(), 201);
    }

    private void toConfirmation(String time) throws Exception {
        JsonNode reply = turn("""
                {"intent":"BOOK","service":"couples massage","participants":2,"date":"%s","time":"%s",
                 "firstName":"Ana","phone":"+38970555666"}""".formatted(day, time));
        assertThat(reply.get("ask").asText()).isEqualTo("CONFIRMATION");
    }

    private JsonNode turn(String extracted) throws Exception {
        return integration(post(api("/conversations/turn")), token, """
                {"provider":"INSTAGRAM","externalUserId":"%s","displayName":"ana.p","message":"...",
                 "extracted":%s,"executionId":"test-1"}""".formatted(USER, extracted), 200);
    }

    private static List<String> codes(JsonNode reply) {
        List<String> codes = new ArrayList<>();
        reply.get("problems").forEach(problem -> codes.add(problem.get("code").asText()));
        return codes;
    }

    private static List<String> optionValues(JsonNode reply) {
        List<String> values = new ArrayList<>();
        reply.get("options").forEach(option -> values.add(option.get("value").asText()));
        return values;
    }

    private static int optionNumber(JsonNode reply, String labelStart) {
        for (JsonNode option : reply.get("options")) {
            if (option.get("label").asText().startsWith(labelStart)) {
                return option.get("number").asInt();
            }
        }
        throw new AssertionError("no option " + labelStart);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
