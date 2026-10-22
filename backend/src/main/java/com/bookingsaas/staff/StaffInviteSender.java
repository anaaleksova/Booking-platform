package com.bookingsaas.staff;

/** Delivers an invitation link. An interface so tests can capture the link instead of emailing it. */
public interface StaffInviteSender {

    void send(String email, String employeeName, String businessName, String link);
}
