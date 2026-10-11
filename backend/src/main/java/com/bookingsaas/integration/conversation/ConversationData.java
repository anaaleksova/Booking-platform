package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.TimeOfDay;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a conversation has collected so far, stored as JSON on the
 * conversation row. Nothing here is trusted as a booking: the reservation
 * endpoint re-validates all of it.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationData {

    // --- booking ------------------------------------------------------------
    private Long serviceId;
    private String serviceName;
    private Integer participants;
    private LocalDate date;
    private LocalTime time;
    private TimeOfDay timePreference;
    /** requirement id → chosen resource ids; an empty list means "no preference". */
    private Map<Long, List<Long>> resourceSelections = new LinkedHashMap<>();
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String discountCode;
    /** The social profile name, kept with the identity when a customer is created. */
    private String displayName;

    // --- cancellation -------------------------------------------------------
    private Long cancelReservationId;

    // --- what was last put in front of the customer --------------------------
    private Ask asked;
    /** The requirement a RESOURCE question is about. */
    private Long askedRequirementId;
    private List<Offer> offers = new ArrayList<>();
    /** Reservations this person may cancel, as offered. */
    private Set<Long> cancellableIds = new LinkedHashSet<>();

    /**
     * One numbered option. {@code value} is what choosing it means: a service
     * id, "HH:mm", an ISO date, a reservation id, or comma-separated resource ids.
     */
    public record Offer(int number, String label, String value) {
    }

    public void clearSlot() {
        time = null;
        resourceSelections = new LinkedHashMap<>();
    }
}
