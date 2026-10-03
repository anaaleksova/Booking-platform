package com.bookingsaas.notification;

/**
 * Delivers a queued notification over one channel.
 *
 * <p>The abstraction the spec asks for: SMS, WhatsApp or Viber support means
 * adding an implementation and a channel value — no reservation or availability
 * code changes.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /**
     * Sends the message.
     *
     * @throws RuntimeException when delivery fails; the dispatcher records the
     *                          failure and retries within the attempt budget
     */
    void send(Notification notification);
}
