package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSignupCodeSender implements SignupCodeSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailSignupCodeSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String fullName, String code) {
        CodeEmail.send(mailSender, properties, email, fullName,
                "Your verification code: " + code,
                "Confirm your email",
                "Enter this code on the booking page to finish creating your account.",
                code);
    }
}
