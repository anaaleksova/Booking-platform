package com.bookingsaas.integration.events;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Signs outbound webhooks so the receiver can tell them from forgeries:
 * {@code X-Booking-Signature: sha256=<hex HMAC-SHA256 of "timestamp.body">}.
 * Including the timestamp lets the receiver reject replays of old deliveries.
 */
public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Booking-Signature";
    public static final String TIMESTAMP_HEADER = "X-Booking-Timestamp";
    public static final String EVENT_HEADER = "X-Booking-Event";
    public static final String DELIVERY_HEADER = "X-Booking-Delivery";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSigner() {
    }

    public static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
