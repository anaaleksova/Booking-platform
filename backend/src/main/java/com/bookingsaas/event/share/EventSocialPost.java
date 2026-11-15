package com.bookingsaas.event.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One attempt to share an event on one platform: what went out, or why Meta refused it. */
@Entity
@Table(name = "event_social_post")
@Getter
@NoArgsConstructor
public class EventSocialPost {

    public enum Platform { FACEBOOK, INSTAGRAM }

    public enum Status { POSTED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(length = 1000)
    private String permalink;

    @Column(length = 1000)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static EventSocialPost posted(Long eventId, Long businessId, Platform platform, String externalId,
                                  String permalink, Instant now) {
        EventSocialPost post = new EventSocialPost(eventId, businessId, platform, Status.POSTED, now);
        post.externalId = externalId;
        post.permalink = permalink;
        return post;
    }

    static EventSocialPost failed(Long eventId, Long businessId, Platform platform, String error, Instant now) {
        EventSocialPost post = new EventSocialPost(eventId, businessId, platform, Status.FAILED, now);
        post.error = error.length() > 1000 ? error.substring(0, 1000) : error;
        return post;
    }

    private EventSocialPost(Long eventId, Long businessId, Platform platform, Status status, Instant now) {
        this.eventId = eventId;
        this.businessId = businessId;
        this.platform = platform;
        this.status = status;
        this.createdAt = now;
    }
}
