package com.bookingsaas.availability;

import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The owner's email about staff time off: when, why, and the bookings in that
 * time that need another person, another time or a cancellation.
 */
@Component
public class EmailTimeOffNoticeSender implements TimeOffNoticeSender {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailTimeOffNoticeSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, StaffTimeOffEntered timeOff) {
        String link = properties.frontendUrl() + "/admin/settings?tab=time-off";
        String period = format(timeOff.start()) + " – " + format(timeOff.end());
        int clashes = timeOff.conflicts().size();
        String headline = timeOff.employeeName() + " is off " + period
                + (timeOff.reason() == null ? "" : " (" + timeOff.reason() + ")") + ".";
        String action = clashes == 0
                ? "No bookings are affected, and no new bookings will be made with them in that time."
                : (clashes == 1 ? "1 booking needs" : clashes + " bookings need")
                + " another person, another time or a cancellation:";

        StringBuilder text = new StringBuilder(headline).append("\n\n").append(action).append('\n');
        StringBuilder rows = new StringBuilder();
        for (ScheduleConflict conflict : timeOff.conflicts()) {
            String line = format(conflict.localStartTime()) + " · " + conflict.serviceName() + " · "
                    + conflict.customerName();
            text.append("- ").append(line).append('\n');
            rows.append("<li style=\"margin:0 0 6px;\">").append(HtmlUtils.htmlEscape(line)).append("</li>");
        }
        text.append("\nOpen Closures & time off:\n").append(link).append('\n');

        String html = "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#fafafa;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fafafa;\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;"
                + "background:#ffffff;border:1px solid #e6e8ee;border-radius:24px;"
                + "font-family:'Segoe UI',Inter,Helvetica,Arial,sans-serif;\">"
                + "<tr><td style=\"padding:32px;\">"
                + "<h1 style=\"margin:0 0 12px;color:#131a2b;font-size:22px;font-weight:800;\">Time off: "
                + HtmlUtils.htmlEscape(timeOff.employeeName()) + "</h1>"
                + "<p style=\"margin:0 0 16px;color:#5a6478;font-size:15px;line-height:1.6;\">"
                + HtmlUtils.htmlEscape(headline) + "</p>"
                + "<p style=\"margin:0 0 8px;color:" + (clashes == 0 ? "#5a6478" : "#b3261e")
                + ";font-size:15px;line-height:1.6;font-weight:" + (clashes == 0 ? "400" : "700") + ";\">"
                + HtmlUtils.htmlEscape(action) + "</p>"
                + (clashes == 0 ? "" : "<ul style=\"margin:0 0 20px;padding-left:20px;color:#131a2b;font-size:14px;\">"
                + rows + "</ul>")
                + "<a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:12px 24px;"
                + "border-radius:999px;background:#2f5bea;color:#ffffff;font-size:15px;font-weight:700;"
                + "text-decoration:none;\">Open Closures &amp; time off</a>"
                + "</td></tr></table></td></tr></table></body></html>";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(properties.notifications().fromAddress(), timeOff.businessName(),
                    "UTF-8"));
            helper.setTo(recipient);
            helper.setSubject("Time off: " + timeOff.employeeName() + ", " + format(timeOff.start())
                    + (clashes == 0 ? "" : " — " + clashes + " booking" + (clashes == 1 ? "" : "s") + " to move"));
            helper.setText(text.toString(), html);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not build the time-off email", ex);
        }
    }

    private static String format(LocalDateTime time) {
        return time.format(WHEN);
    }
}
