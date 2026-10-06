package com.bookingsaas.notification;

import com.bookingsaas.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * The one channel implemented for the MVP.
 *
 * <p>Sends plain text and, when present, the branded HTML as a multipart
 * message. Sending both, from a named sender with a reply address, is also what
 * spam filters expect of a legitimate transactional email.
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailNotificationSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = properties.notifications().fromAddress();
            helper.setFrom(notification.getSenderName() == null || notification.getSenderName().isBlank()
                    ? new InternetAddress(from)
                    : new InternetAddress(from, notification.getSenderName(), "UTF-8"));
            helper.setTo(notification.getRecipient());
            if (notification.getReplyTo() != null && !notification.getReplyTo().isBlank()) {
                helper.setReplyTo(notification.getReplyTo());
            }
            helper.setSubject(notification.getSubject());
            if (notification.getHtmlBody() != null) {
                helper.setText(notification.getBody(), notification.getHtmlBody());
            } else {
                helper.setText(notification.getBody(), false);
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not build the email: " + ex.getMessage(), ex);
        }
        mailSender.send(message);
        // Recipient addresses are personal data: log the row, not the person.
        log.debug("Sent notification id={} type={}", notification.getId(), notification.getType());
    }
}
