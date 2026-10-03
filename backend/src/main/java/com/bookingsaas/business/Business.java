package com.bookingsaas.business;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;
import java.util.UUID;

/**
 * A tenant. Every other business-owned entity points back here, and the whole
 * authorization model is "does the caller belong to this business?".
 */
@Entity
@Table(name = "business")
@Getter
@Setter
@NoArgsConstructor
public class Business extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Public page identifier: {@code /b/{slug}}. Globally unique. */
    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    /** One line under the name on the booking page. */
    @Column(length = 200)
    private String tagline;

    /** Brand colour of the customer pages, "#rrggbb". */
    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor = "#2f5bea";

    @Column(name = "logo_media_id")
    private UUID logoMediaId;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    /** Whether the booking page embeds a map of the address. */
    @Column(name = "show_map", nullable = false)
    private boolean showMap = true;

    /** Whether the booking page shows the photo gallery (when it has photos). */
    @Column(name = "show_gallery", nullable = false)
    private boolean showGallery = true;

    @Column(length = 40)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(length = 500)
    private String address;

    /** ISO-4217 code. Every price of the business is expressed in it. */
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    /**
     * IANA zone id, e.g. {@code Europe/Skopje}. Availability, the calendar and
     * notifications are all rendered in this zone — never the server's.
     */
    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean active = true;

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
