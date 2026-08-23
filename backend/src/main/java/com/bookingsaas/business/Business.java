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

/**
 * A tenant. Every other business-owned entity points back here, and the whole
 * authorization model is "does the caller belong to this business?".
 */
@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
public class Business extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Public booking page identifier: {@code /book/{slug}}. Globally unique. */
    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(length = 40)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(length = 500)
    private String address;

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
