package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/**
 * The one look of every "here is your code" email (sign-up, password reset):
 * a card with a headline, a sentence of explanation and the code in large type.
 * Sent straight away rather than through the notification outbox, because the
 * person is on screen waiting for it.
 */
final class CodeEmail {

    private CodeEmail() {
    }

    static void send(JavaMailSender mailSender, AppProperties properties, String email, String fullName,
                     String subject, String headline, String instruction, String code) {
        String text = "Hello " + fullName + ",\n\n"
                + instruction + "\n\n"
                + "Your code: " + code + "\n\n"
                + "It expires in 15 minutes. If you did not ask for this, you can ignore this email.\n";
        String html = "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#fafafa;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fafafa;\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;"
                + "background:#ffffff;border:1px solid #e6e8ee;border-radius:24px;"
                + "font-family:'Segoe UI',Inter,Helvetica,Arial,sans-serif;\">"
                + "<tr><td style=\"padding:32px;text-align:center;\">"
                + "<p style=\"margin:0 0 6px;color:#5a6478;font-size:15px;\">Hello " + HtmlUtils.htmlEscape(fullName) + ",</p>"
                + "<h1 style=\"margin:0 0 20px;color:#131a2b;font-size:24px;font-weight:800;\">"
                + HtmlUtils.htmlEscape(headline) + "</h1>"
                + "<p style=\"margin:0 0 20px;color:#5a6478;font-size:15px;line-height:1.6;\">"
                + HtmlUtils.htmlEscape(instruction) + "</p>"
                + "<div style=\"display:inline-block;padding:16px 28px;border-radius:16px;background:#f2f4f8;"
                + "color:#131a2b;font-size:32px;font-weight:800;letter-spacing:8px;\">" + code + "</div>"
                + "<p style=\"margin:20px 0 0;color:#5a6478;font-size:13px;line-height:1.6;\">"
                + "The code expires in 15 minutes. If you did not ask for this, you can ignore this email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(properties.notifications().fromAddress(), "Online reservations", "UTF-8"));
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Could not build the email", ex);
        }
    }
}
