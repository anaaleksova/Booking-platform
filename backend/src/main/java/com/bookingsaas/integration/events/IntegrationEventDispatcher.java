package com.bookingsaas.integration.events;

import com.bookingsaas.integration.Integration;
import com.bookingsaas.integration.PlatformAutomation;
import com.bookingsaas.integration.PlatformAutomation.WebhookTarget;
import com.bookingsaas.reservation.ReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Delivers queued events to the business's n8n webhook — the platform's own
 * n8n for a business connected with Facebook (see {@link PlatformAutomation}).
 *
 * <p>Each delivery is a small signed JSON document naming the event and the
 * reservation; the workflow fetches the details it needs through the
 * integration API with its own token. That keeps customer data out of
 * webhook bodies and means a delayed retry never carries stale details.
 *
 * <p>Deliberately not one transaction around the batch: HTTP calls must not
 * hold a database connection, and each event's outcome is saved on its own.
 */
@Component
public class IntegrationEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventDispatcher.class);
    private static final int BATCH_SIZE = 25;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Longer than a whole batch can take (25 posts with 5 s connect + 10 s response timeouts). */
    private static final Duration LEASE = Duration.ofMinutes(10);

    private final IntegrationEventRepository repository;
    private final PlatformAutomation platformAutomation;
    private final ReservationRepository reservations;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final TransactionTemplate transaction;
    private final HttpClient http = HttpClient.newBuilder()
            // HTTP/2 would start with an h2c upgrade on plain http, which some
            // webhook receivers never answer.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public IntegrationEventDispatcher(IntegrationEventRepository repository,
                                      PlatformAutomation platformAutomation,
                                      ReservationRepository reservations,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      @Value("${app.integrations.events-enabled:true}") boolean enabled,
                                      PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.repository = repository;
        this.platformAutomation = platformAutomation;
        this.reservations = reservations;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** The outcome of one delivery attempt. */
    public record DeliveryResult(boolean delivered, Integer httpStatus, String error) {
    }

    @Scheduled(fixedDelayString = "${app.integrations.dispatch-interval-ms:10000}")
    public void dispatchDue() {
        if (!enabled) {
            return;
        }
        for (IntegrationEvent event : claimDue()) {
            dispatch(event);
        }
    }

    /**
     * Locks and leases the next batch in one short transaction, so a second
     * instance does not deliver the same events. An instance that dies
     * mid-batch leaves its events to be retried when the lease runs out.
     */
    List<IntegrationEvent> claimDue() {
        List<IntegrationEvent> claimed = transaction.execute(status -> {
            Instant now = clock.instant();
            List<Long> ids = repository.lockDue(now, BATCH_SIZE);
            if (ids.isEmpty()) {
                return List.<IntegrationEvent>of();
            }
            repository.lease(ids, now.plus(LEASE));
            return repository.findAllById(ids);
        });
        return claimed == null ? List.of() : claimed;
    }

    void dispatch(IntegrationEvent event) {
        Optional<WebhookTarget> target = platformAutomation.webhookFor(event.getBusinessId());
        if (target.isEmpty()) {
            event.markSkipped("n8n is not connected");
            repository.save(event);
            return;
        }
        if (IntegrationEventTypes.RESERVATION_REMINDER_DUE.equals(event.getEventType()) && !stillAhead(event)) {
            event.markSkipped("Reservation is no longer active");
            repository.save(event);
            return;
        }

        DeliveryResult result = post(target.get(), event.getEventType(), event.getDeliveryId(), payload(event));
        if (result.delivered()) {
            event.markDelivered(clock.instant(), result.httpStatus());
            log.info("Delivered {} for reservation {} (delivery {})", event.getEventType(), event.getReservationId(),
                    event.getDeliveryId());
        } else {
            event.markAttemptFailed(clock.instant(), result.httpStatus(), result.error());
            log.warn("Delivery of {} {} failed (attempt {}): {}", event.getEventType(), event.getDeliveryId(),
                    event.getAttempts(), result.error());
        }
        repository.save(event);
    }

    /** Sends a test event straight away, for the admin's "Send test event" button. */
    public DeliveryResult sendTest(Integration integration) {
        Map<String, Object> body = new LinkedHashMap<>();
        UUID deliveryId = UUID.randomUUID();
        body.put("event", IntegrationEventTypes.INTEGRATION_TEST);
        body.put("deliveryId", deliveryId);
        body.put("occurredAt", clock.instant());
        body.put("businessId", integration.getBusinessId());
        return post(new WebhookTarget(integration.getWebhookUrl(), integration.getSigningSecret()),
                IntegrationEventTypes.INTEGRATION_TEST, deliveryId, body);
    }

    private Map<String, Object> payload(IntegrationEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event.getEventType());
        body.put("deliveryId", event.getDeliveryId());
        body.put("occurredAt", event.getCreatedAt());
        body.put("businessId", event.getBusinessId());
        body.put("reservationId", event.getReservationId());
        body.put("attempt", event.getAttempts() + 1);
        return body;
    }

    private DeliveryResult post(WebhookTarget target, String eventType, UUID deliveryId, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            long timestamp = clock.instant().getEpochSecond();
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "booking-platform-webhooks")
                    .header(WebhookSigner.EVENT_HEADER, eventType)
                    .header(WebhookSigner.DELIVERY_HEADER, deliveryId.toString())
                    .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (target.signingSecret() != null) {
                request.header(WebhookSigner.SIGNATURE_HEADER,
                        WebhookSigner.sign(target.signingSecret(), timestamp, json));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            return status >= 200 && status < 300
                    ? new DeliveryResult(true, status, null)
                    : new DeliveryResult(false, status, "HTTP " + status);
        } catch (JsonProcessingException ex) {
            return new DeliveryResult(false, null, "Could not serialise the event");
        } catch (IOException | IllegalArgumentException ex) {
            return new DeliveryResult(false, null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DeliveryResult(false, null, "Interrupted");
        }
    }

    private boolean stillAhead(IntegrationEvent event) {
        Instant now = clock.instant();
        return event.getReservationId() != null && reservations.findById(event.getReservationId())
                .filter(reservation -> reservation.getStatus().isActive() && reservation.getStartTime().isAfter(now))
                .isPresent();
    }

    /** Recent deliveries for the admin page. */
    public List<IntegrationEvent> recent(Long businessId, int limit) {
        return repository.findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, limit));
    }
}
