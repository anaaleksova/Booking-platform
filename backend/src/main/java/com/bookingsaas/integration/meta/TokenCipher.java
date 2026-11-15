package com.bookingsaas.integration.meta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the Meta tokens the platform keeps (AES-256-GCM) and signs the
 * OAuth state, both with {@code META_TOKEN_KEY}. A database dump alone does
 * not give anyone a business's Page.
 */
@Component
public class TokenCipher {

    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final SecretKeySpec signingKey;

    public TokenCipher(@Value("${app.meta.token-key:}") String base64Key) {
        byte[] raw = decodeKey(base64Key);
        this.key = raw == null ? null : new SecretKeySpec(raw, "AES");
        // A separate key for signing, so the two uses never share one.
        this.signingKey = raw == null ? null : new SecretKeySpec(sha256("state:", raw), "HmacSHA256");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt the token", ex);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        if (stored == null || !stored.startsWith(VERSION)) {
            throw new IllegalStateException("Unknown token format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Most often: META_TOKEN_KEY changed since the token was stored.
            throw new IllegalStateException("Could not decrypt the token", ex);
        }
    }

    /** URL-safe HMAC-SHA256 of the text. */
    public String sign(String text) {
        requireKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not sign", ex);
        }
    }

    public boolean verify(String text, String signature) {
        return signature != null && MessageDigest.isEqual(sign(text).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("META_TOKEN_KEY is not set");
        }
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("META_TOKEN_KEY must be base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("META_TOKEN_KEY must be 32 bytes (base64 of 32 random bytes)");
        }
        return raw;
    }

    private static byte[] sha256(String label, byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(label.getBytes(StandardCharsets.UTF_8));
            return digest.digest(raw);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
