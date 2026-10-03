package com.bookingsaas.notification;

import com.bookingsaas.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the outbox on a timer.
 *
 * <p>Deliberately simple: one small batch per tick, retried on the next tick if
 * a send fails. No broker, no queue infrastructure — a reservation MVP does not
 * need one, and the outbox already provides the durability that matters.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH_SIZE = 50;

    private final NotificationRepository repository;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final AppProperties properties;
    private final Clock clock;

    public NotificationDispatcher(NotificationRepository repository,
                                  List<NotificationSender> senders,
                                  AppProperties properties,
                                  Clock clock) {
        this.repository = repository;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval-ms:30000}")
    @Transactional
    public void dispatchDue() {
        if (!properties.notifications().enabled()) {
            return;
        }
        List<Notification> due = repository.findDue(clock.instant(), PageRequest.of(0, BATCH_SIZE));
        for (Notification notification : due) {
            NotificationSender sender = senders.get(notification.getChannel());
            if (sender == null) {
                notification.markSkipped("No sender configured for channel " + notification.getChannel());
                repository.save(notification);
                continue;
            }
            try {
                sender.send(notification);
                notification.markSent(clock.instant());
            } catch (RuntimeException ex) {
                notification.markAttemptFailed(ex.getMessage());
                log.warn("Notification {} failed (attempt {}): {}",
                        notification.getId(), notification.getAttempts(), ex.getMessage());
            }
            repository.save(notification);
        }
    }
}
