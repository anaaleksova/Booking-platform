package com.bookingsaas.integration.api;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.reservation.dto.ResourceSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shapes of the integration API — written for machines (n8n, scripts), so
 * every field is stable, times are wall-clock in the business timezone and
 * nothing admin-only is included.
 */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    // --- business & services ----------------------------------------------

    /**
     * @param bookingPageUrl  where a customer can book on the website instead
     * @param channels        which channel ids belong to this business, so a
     *                        workflow can check a message really is for it
     */
    public record BusinessInfo(Long id, String name, String slug, String description, String phone, String email,
                               String address, String timezone, String currency, String bookingPageUrl,
                               List<DayInterval> openingHours, Policies policies, List<Channel> channels) {
    }

    public record Policies(int minimumBookingNoticeMinutes, int maximumBookingAdvanceDays,
                           boolean customerCancellationAllowed, int cancellationDeadlineHours) {
    }

    public record Channel(String provider, String status, String externalAccountId) {
    }

    /**
     * @param resourceSelection whether, and for what, the customer may pick
     *                          resources ("choose your therapist"); empty when
     *                          the business assigns everything
     */
    public record ServiceInfo(Long id, String name, String description, String category, int durationMinutes,
                              BigDecimal price, boolean pricePerParticipant, int maxParticipants, String currency,
                              List<ChoosableRequirement> resourceSelection) {
    }

    public record ChoosableRequirement(Long requirementId, String resourceTypeName, int quantity,
                                       List<NamedResource> resources) {
    }

    public record NamedResource(Long id, String name) {
    }

    // --- availability & price -----------------------------------------------

    /**
     * @param availableSlots start times on {@code date} that can be booked right
     *                       now for this group, computed by the same engine as
     *                       the website
     */
    public record AvailabilityResponse(Long serviceId, LocalDate date, int numberOfParticipants,
                                       int durationMinutes, String timezone, List<Slot> availableSlots) {
    }

    /**
     * @param start       "HH:mm", business-local
     * @param startTime   the value to send back when reserving
     * @param resourceOptions only for requirements the customer may choose
     */
    public record Slot(String start, String end, LocalDateTime startTime, List<ResourceOptions> resourceOptions) {
    }

    /** One way to satisfy a choosable requirement: a single resource, or several combined for a group. */
    public record ResourceOptions(Long requirementId, String resourceTypeName, List<ResourceChoice> choices) {
    }

    public record ResourceChoice(List<NamedResource> resources, String area) {
    }

    public record QuoteRequest(@NotNull Long serviceId, @NotNull @Min(1) Integer numberOfParticipants,
                               @Size(max = 40) String discountCode) {
    }

    public record Quote(BigDecimal originalPrice, BigDecimal discountAmount, BigDecimal finalPrice, String currency,
                        String discountCode, String discountName) {
    }

    // --- customers -----------------------------------------------------------

    /**
     * Find-or-create a customer for a social user. When the user is already
     * linked, the details are ignored and the linked customer is returned.
     */
    public record CustomerRequest(
            @NotNull ChannelProvider provider,
            @NotBlank @Size(max = 128) String externalUserId,
            @Size(max = 160) String displayName,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Email @Size(max = 320) String email
    ) {
    }

    /**
     * @param linked   whether the social user is linked to this customer (and
     *                 so will be recognised next time)
     * @param created  whether the record was created by this request
     */
    public record CustomerInfo(Long id, String firstName, String lastName, String phone, String email,
                               boolean linked, boolean created) {
    }

    // --- reservations --------------------------------------------------------

    /**
     * Either {@code customerId} or {@code externalCustomer} names the customer.
     * No price, no resource ids outside {@code resourceSelections}: the server
     * computes the price and validates every choice.
     *
     * @param conversationId set by the chat workflow so the conversation is
     *                       closed in the same transaction as the booking
     */
    public record CreateReservationRequest(
            @NotNull Long serviceId,
            @NotNull @Min(1) Integer numberOfParticipants,
            @NotNull LocalDateTime startTime,
            @Size(max = 20) List<@Valid ResourceSelection> resourceSelections,
            @Size(max = 40) String discountCode,
            Long customerId,
            @Valid CustomerRequest externalCustomer,
            @Size(max = 2000) String notes,
            Long conversationId
    ) {
    }

    /**
     * @param cancellationReason {@link IntegrationReservationService#CHAT_CANCELLATION_REASON} when the
     *                           customer cancelled in a chat, so workflows need not tell them again
     * @param manageUrl the customer's own page for this reservation on the website
     * @param channels  where the customer can be messaged, e.g. INSTAGRAM:1789...
     */
    public record ReservationInfo(
            Long id,
            UUID reference,
            Long businessId,
            String status,
            String source,
            Long serviceId,
            String serviceName,
            Long customerId,
            String customerName,
            String customerFirstName,
            int numberOfParticipants,
            LocalDate date,
            String start,
            String end,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timezone,
            List<AssignedResource> resources,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal finalPrice,
            String currency,
            String discountCode,
            boolean cancellableByCustomer,
            String cancellationReason,
            String manageUrl,
            List<CustomerChannel> channels
    ) {
    }

    public record AssignedResource(String resourceTypeName, String name) {
    }

    public record CustomerChannel(ChannelProvider provider, String externalUserId) {
    }

    public record CancelReservationRequest(@Size(max = 255) String reason, Long conversationId) {
    }
}
