package com.bookingsaas.notification;

import com.bookingsaas.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the outbox on a timer.
 *
 * <p>Deliberately simple: no broker, no queue infrastructure. The outbox
 * already provides the durability that matters. Each tick:
 *
 * <ol>
 *   <li>claims a small batch in one short transaction: the rows are locked
 *       with {@code SKIP LOCKED} and leased (pushed into the future), so
 *       another instance cannot pick the same messages;</li>
 *   <li>sends them with no transaction open, so a slow mail server never
 *       holds a database connection;</li>
 *   <li>records each outcome on its own. A failed send is retried with a
 *       back-off; an instance that dies mid-batch leaves its claimed messages
 *       to be picked up again when the lease runs out.</li>
 * </ol>
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH_SIZE = 20;
    /** Longer than a whole batch can take (20 sends with 5-second SMTP timeouts). */
    private static final Duration LEASE = Duration.ofMinutes(10);

    private final NotificationRepository repository;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NotificationDispatcher(NotificationRepository repository,
                                  List<NotificationSender> senders,
                                  AppProperties properties,
                                  Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.repository = repository;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval-ms:30000}")
    public void dispatchDue() {
        if (!properties.notifications().enabled()) {
            return;
        }
        for (Notification notification : claimDue()) {
            NotificationSender sender = senders.get(notification.getChannel());
            if (sender == null) {
                notification.markSkipped("No sender configured for channel " + notification.getChannel());
            } else {
                try {
                    sender.send(notification);
                    notification.markSent(clock.instant());
                } catch (RuntimeException ex) {
                    notification.markAttemptFailed(clock.instant(), ex.getMessage());
                    log.warn("Notification {} failed (attempt {}): {}",
                            notification.getId(), notification.getAttempts(), ex.getMessage());
                }
            }
            repository.save(notification);
        }
    }

    /** Locks and leases the next batch; the returned messages belong to this worker until the lease ends. */
    List<Notification> claimDue() {
        List<Notification> claimed = transaction.execute(status -> {
            Instant now = clock.instant();
            List<Long> ids = repository.lockDue(now, BATCH_SIZE);
            if (ids.isEmpty()) {
                return List.<Notification>of();
            }
            repository.lease(ids, now.plus(LEASE));
            return repository.findAllById(ids);
        });
        return claimed == null ? List.of() : claimed;
    }
}
