package com.bookingsaas.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who runs the platform itself: the accounts named in {@code PLATFORM_ADMIN_EMAILS}
 * (comma-separated). Deliberately configuration, not data: no screen and no
 * API can make anyone a platform administrator, so no bug in the app can either.
 */
@Component
public class PlatformAdmins {

    private final Set<String> emails;

    public PlatformAdmins(@Value("${app.platform.admin-emails:}") String configured) {
        this.emails = Arrays.stream(configured.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPlatformAdmin(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /** The configured addresses, lower-case. */
    public Set<String> emails() {
        return emails;
    }
}
