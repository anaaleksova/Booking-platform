package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.api.IntegrationDtos.CreateReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.Confirmation;
import com.bookingsaas.integration.conversation.ConversationEnums.Intent;
import com.bookingsaas.integration.conversation.ConversationEnums.NextAction;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.TimeOfDay;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The conversation API.
 *
 * <p>The division of labour: the language model in n8n turns a message into an
 * {@link Extraction} and turns a {@link TurnResponse} into a friendly reply.
 * Everything in between — which question comes next, whether a time is free,
 * what it costs, when a reservation may be made — is decided here.
 */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    /** One incoming message, already interpreted. */
    public record TurnRequest(
            @NotNull ChannelProvider provider,
            @NotBlank @Size(max = 128) String externalUserId,
            @Size(max = 160) String displayName,
            @Size(max = 2000) String message,
            @Valid Extraction extracted,
            @Size(max = 128) String executionId
    ) {
    }

    /**
     * What the customer said, as structured data. Every field is optional: a
     * message usually answers one question. Values are what the customer
     * expressed — never inferred availability or prices.
     *
     * @param intent        the customer's goal; null when the message just
     *                      answers the current question
     * @param service       the service as the customer named it
     * @param date          resolved to an ISO date, e.g. "tomorrow" → 2026-10-07
     * @param time          "HH:mm", 24-hour
     * @param optionNumber  the number of an offered option the customer chose
     * @param noPreference  "any is fine" — for a resource question
     * @param abort         the customer wants to stop the current conversation
     */
    public record Extraction(
            Intent intent,
            @Size(max = 160) String service,
            Long serviceId,
            @Min(1) @Max(1000) Integer participants,
            LocalDate date,
            @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm") String time,
            TimeOfDay timePreference,
            @Min(1) @Max(100) Integer optionNumber,
            Confirmation confirmation,
            Boolean noPreference,
            Boolean abort,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Size(max = 320) String email,
            @Size(max = 40) String discountCode
    ) {
        public static Extraction empty() {
            return new Extraction(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null);
        }
    }

    /**
     * What to say next. The reply must be written from these facts only.
     *
     * @param ask         the question the reply should ask
     * @param options     numbered choices to present (services, times, ...)
     * @param problems    things to tell the customer first, e.g. a time that is not free
     * @param summary     the booking as it would be made, with the server's price
     * @param outcome     set when the conversation ended
     * @param action      for CREATE_RESERVATION / CANCEL_RESERVATION: exactly what to send
     */
    public record TurnResponse(
            Long conversationId,
            String flow,
            String state,
            NextAction nextAction,
            Ask ask,
            List<Option> options,
            List<Problem> problems,
            List<String> missingFields,
            Summary summary,
            ReservationInfo reservation,
            List<ReservationInfo> reservations,
            Outcome outcome,
            Action action,
            CustomerFacts customer,
            BusinessFacts business
    ) {
    }

    public record Option(int number, String label, String value) {
    }

    /** @param code stable, for branching; {@code message} is a plain-language hint */
    public record Problem(String code, String message) {
    }

    /**
     * The booking so far. Filled progressively: early questions see only the
     * service, the final confirmation sees everything including the price.
     *
     * @param resources chosen resource names; empty when the business assigns them
     */
    public record Summary(String serviceName, Integer numberOfParticipants, Integer maxParticipants, LocalDate date,
                          String dayOfWeek, String start, String end, List<String> resources, String customerName,
                          Quote price, String timezone) {
    }

    /**
     * @param idempotencyKey send as the Idempotency-Key header
     * @param reservation    the body for POST /reservations
     * @param reservationId  for a cancellation
     */
    public record Action(String idempotencyKey, CreateReservationRequest reservation, Long reservationId) {
    }

    public record CustomerFacts(boolean known, String firstName) {
    }

    /** What the reply may say about the business, e.g. to answer a question. */
    public record BusinessFacts(String name, String phone, String address, String bookingPageUrl,
                                String openingHours, List<ServiceFact> services) {
    }

    public record ServiceFact(String name, String category, int durationMinutes, BigDecimal price,
                              boolean pricePerParticipant, int maxParticipants, String currency) {
    }

    /** Where a conversation stands, for interpreting the next message. */
    public record ConversationContext(
            Long conversationId,
            String flow,
            String state,
            Ask asked,
            List<Option> options,
            Collected collected,
            CustomerFacts customer,
            LocalDate today,
            LocalDateTime now,
            String timezone,
            List<String> serviceNames
    ) {
    }

    public record Collected(String serviceName, Integer participants, LocalDate date, String time,
                            TimeOfDay timePreference, String firstName, String phone, String discountCode) {
    }

    /** The reservation or cancellation the conversation asked for could not be done. */
    public record ActionFailedRequest(@NotBlank @Size(max = 60) String errorCode,
                                      @Size(max = 1000) String message,
                                      @Size(max = 128) String executionId) {
    }
}
