package com.bookingsaas.availability;

/** Tells the owner that a staff member entered time off. */
public interface TimeOffNoticeSender {

    void send(String recipient, StaffTimeOffEntered timeOff);
}
