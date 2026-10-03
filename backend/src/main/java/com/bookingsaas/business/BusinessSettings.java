package com.bookingsaas.business;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;

/**
 * Per-tenant booking rules. The availability engine and the booking service
 * read these instead of embedding any constants of their own.
 */
@Entity
@Table(name = "business_settings")
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, unique = true)
    private Long businessId;

    /** How far ahead of "now" the earliest bookable slot sits. */
    @Column(name = "minimum_booking_notice_minutes", nullable = false)
    private int minimumBookingNoticeMinutes = 60;

    /** How far into the future the booking window extends. */
    @Column(name = "maximum_booking_advance_days", nullable = false)
    private int maximumBookingAdvanceDays = 60;

    /** Granularity of generated candidate start times. */
    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes = 30;

    @Column(name = "allow_customer_cancellation", nullable = false)
    private boolean allowCustomerCancellation = true;

    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours = 12;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "reminder_notifications_enabled", nullable = false)
    private boolean reminderNotificationsEnabled = true;

    @Column(name = "reminder_lead_hours", nullable = false)
    private int reminderLeadHours = 24;

    public BusinessSettings(Long businessId) {
        this.businessId = businessId;
    }

    public Duration minimumNotice() {
        return Duration.ofMinutes(minimumBookingNoticeMinutes);
    }

    public Duration slotInterval() {
        return Duration.ofMinutes(slotIntervalMinutes);
    }

    public Duration cancellationDeadline() {
        return Duration.ofHours(cancellationDeadlineHours);
    }
}
