package com.bookingsaas.availability;

import com.bookingsaas.business.BusinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the owner once staff time off is saved. A failed email is logged, not
 * thrown: the time off is in the calendar either way, and the bookings it hits
 * are flagged on the owner's Closures & time off tab.
 */
@Component
public class StaffTimeOffListener {

    private static final Logger log = LoggerFactory.getLogger(StaffTimeOffListener.class);

    private final BusinessService businessService;
    private final TimeOffNoticeSender sender;

    public StaffTimeOffListener(BusinessService businessService, TimeOffNoticeSender sender) {
        this.businessService = businessService;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StaffTimeOffEntered timeOff) {
        String recipient = businessService.notificationEmailOf(timeOff.businessId());
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        try {
            sender.send(recipient, timeOff);
        } catch (RuntimeException ex) {
            log.warn("Could not email business {} about time off of {}: {}", timeOff.businessId(),
                    timeOff.employeeName(), ex.getMessage());
        }
    }
}
