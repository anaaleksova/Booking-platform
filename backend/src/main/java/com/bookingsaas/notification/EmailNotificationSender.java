package com.bookingsaas.notification;

import com.bookingsaas.common.calendar.Ics;
import com.bookingsaas.common.config.AppProperties;
import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * The one channel implemented for the MVP.
 *
 * <p>Sends plain text and, when present, the branded HTML as a multipart
 * message. Sending both, from a named sender with a reply address, is also what
 * spam filters expect of a legitimate transactional email.
 *
 * <p>A calendar invitation (the owner's messages) is sent the way meeting
 * invitations are: the {@code text/calendar} part sits next to the text and
 * HTML as another version of the message, which is what Gmail and Outlook
 * look for to put it in the calendar — plus the same file attached, for
 * clients that only look at attachments.
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
        String ics = notification.getCalendarIcs();
        String method = ics == null ? null : Ics.methodOf(ics);
        boolean invitation = "REQUEST".equals(method) || "CANCEL".equals(method);
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, !invitation, "UTF-8");
            String from = properties.notifications().fromAddress();
            helper.setFrom(notification.getSenderName() == null || notification.getSenderName().isBlank()
                    ? new InternetAddress(from)
                    : new InternetAddress(from, notification.getSenderName(), "UTF-8"));
            helper.setTo(notification.getRecipient());
            if (notification.getReplyTo() != null && !notification.getReplyTo().isBlank()) {
                helper.setReplyTo(notification.getReplyTo());
            }
            helper.setSubject(notification.getSubject());
            if (invitation) {
                message.setContent(invitationContent(notification, ics, method));
            } else {
                if (notification.getHtmlBody() != null) {
                    helper.setText(notification.getBody(), notification.getHtmlBody());
                } else {
                    helper.setText(notification.getBody(), false);
                }
                if (ics != null) {
                    helper.addAttachment("reservation.ics", new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
                            "text/calendar; charset=UTF-8; method=" + method);
                }
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not build the email: " + ex.getMessage(), ex);
        }
        mailSender.send(message);
        // Recipient addresses are personal data: log the row, not the person.
        log.debug("Sent notification id={} type={}", notification.getId(), notification.getType());
    }

    /** mixed( alternative( text, html, calendar ), invite.ics ). */
    private static MimeMultipart invitationContent(Notification notification, String ics, String method)
            throws MessagingException {
        String calendarType = "text/calendar; charset=UTF-8; method=" + method;
        byte[] bytes = ics.getBytes(StandardCharsets.UTF_8);

        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart text = new MimeBodyPart();
        text.setText(notification.getBody(), "UTF-8");
        alternative.addBodyPart(text);
        if (notification.getHtmlBody() != null) {
            MimeBodyPart html = new MimeBodyPart();
            html.setText(notification.getHtmlBody(), "UTF-8", "html");
            alternative.addBodyPart(html);
        }
        MimeBodyPart calendar = new MimeBodyPart();
        calendar.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, calendarType)));
        calendar.setHeader("Content-Type", calendarType);
        alternative.addBodyPart(calendar);

        MimeBodyPart body = new MimeBodyPart();
        body.setContent(alternative);
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, "application/ics")));
        attachment.setHeader("Content-Type", "application/ics; name=\"invite.ics\"");
        attachment.setDisposition(Part.ATTACHMENT);
        attachment.setFileName("invite.ics");

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(body);
        mixed.addBodyPart(attachment);
        return mixed;
    }
}
