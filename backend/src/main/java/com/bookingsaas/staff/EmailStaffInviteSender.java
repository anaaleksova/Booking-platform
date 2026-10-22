package com.bookingsaas.staff;

import com.bookingsaas.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/**
 * The invitation email: who invited them, what they will be able to do, and
 * one button. Sent straight away, like sign-up codes, because the owner is on
 * screen and wants to know it went out.
 */
@Component
public class EmailStaffInviteSender implements StaffInviteSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailStaffInviteSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String employeeName, String businessName, String link) {
        String intro = businessName + " invited you to log in and see the reservations assigned to you.";
        String text = "Hello " + employeeName + ",\n\n" + intro + "\n\n"
                + "Accept the invitation:\n" + link + "\n\n"
                + "The link works for 7 days. If you were not expecting this, you can ignore this email.\n";
        String html = "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#fafafa;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fafafa;\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;"
                + "background:#ffffff;border:1px solid #e6e8ee;border-radius:24px;"
                + "font-family:'Segoe UI',Inter,Helvetica,Arial,sans-serif;\">"
                + "<tr><td style=\"padding:32px;text-align:center;\">"
                + "<p style=\"margin:0 0 6px;color:#5a6478;font-size:15px;\">Hello " + HtmlUtils.htmlEscape(employeeName)
                + ",</p>"
                + "<h1 style=\"margin:0 0 16px;color:#131a2b;font-size:24px;font-weight:800;\">Join "
                + HtmlUtils.htmlEscape(businessName) + "</h1>"
                + "<p style=\"margin:0 0 24px;color:#5a6478;font-size:15px;line-height:1.6;\">"
                + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:14px 28px;"
                + "border-radius:999px;background:#2f5bea;color:#ffffff;font-size:15px;font-weight:700;"
                + "text-decoration:none;\">Accept invitation</a>"
                + "<p style=\"margin:24px 0 0;color:#5a6478;font-size:13px;line-height:1.6;\">"
                + "The link works for 7 days. If you were not expecting this, you can ignore this email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(properties.notifications().fromAddress(), businessName, "UTF-8"));
            helper.setTo(email);
            helper.setSubject(businessName + " invited you to see your reservations");
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not build the invitation email", ex);
        }
    }
}
