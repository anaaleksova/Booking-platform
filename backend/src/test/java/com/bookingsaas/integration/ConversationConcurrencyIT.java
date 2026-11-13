package com.bookingsaas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Messages that arrive close together: handled one after the other per
 * person, and a message resent before it was answered is not answered twice.
 */
@DisplayName("conversation concurrency")
class ConversationConcurrencyIT extends AbstractSpaIntegrationTest {

    @Test
    void messages_sent_at_once_both_count_instead_of_one_overwriting_the_other() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Several people, to give the race every chance to show.
            for (int person = 1; person <= 5; person++) {
                String user = "ig-rapid-" + person;
                CountDownLatch start = new CountDownLatch(1);
                List<Future<JsonNode>> sent = new ArrayList<>();
                sent.add(pool.submit(() -> {
                    start.await();
                    return turn(user, "classic massage", """
                            {"intent":"BOOK","service":"Classic Massage"}""", null);
                }));
                sent.add(pool.submit(() -> {
                    start.await();
                    return turn(user, "tomorrow", """
                            {"date":"%s"}""".formatted(day), null);
                }));
                start.countDown();
                for (Future<JsonNode> reply : sent) {
                    assertThat(reply.get().get("conversationId")).isNotNull();
                }
                // Whichever came first, the conversation now knows both the service and the date.
                JsonNode next = turn(user, "?", "{}", null);
                assertThat(next.get("ask").asText()).as(user).isEqualTo("TIME");
                assertThat(next.get("summary").get("serviceName").asText()).isEqualTo("Classic Massage");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a_message_resent_before_the_answer_is_not_answered_again() throws Exception {
        String user = "ig-impatient";
        Instant firstSent = Instant.now().minusSeconds(60);
        JsonNode first = turn(user, "Classic massage please", """
                {"intent":"BOOK","service":"Classic Massage"}""", firstSent);
        assertThat(first.get("nextAction").asText()).isEqualTo("REPLY");
        assertThat(first.get("ask").asText()).isEqualTo("DATE");

        // Sent again 30 seconds later, while the first was still being answered.
        JsonNode resent = turn(user, "classic  MASSAGE please ", """
                {"intent":"BOOK","service":"Classic Massage"}""", firstSent.plusSeconds(30));
        assertThat(resent.get("nextAction").asText()).isEqualTo("NONE");
        assertThat(resent.get("state").asText()).isEqualTo(first.get("state").asText());

        // The same words after the answer are a new message.
        JsonNode again = turn(user, "Classic massage please", "{}", Instant.now().plusSeconds(1));
        assertThat(again.get("nextAction").asText()).isEqualTo("REPLY");
    }

    private JsonNode turn(String user, String message, String extracted, Instant sentAt) throws Exception {
        return integration(post(api("/conversations/turn")), token, """
                {"provider":"INSTAGRAM","externalUserId":"%s","displayName":"ana.p","message":"%s",
                 "extracted":%s,"executionId":"test-1"%s}""".formatted(user, message, extracted,
                sentAt == null ? "" : ",\"sentAt\":\"" + sentAt + "\""), 200);
    }
}
