package com.bookingsaas.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The "Getting started" checklist of a business created from a template.
 * Each timestamp is when the owner did that step; businesses from before
 * templates have no row and so no checklist.
 */
@Entity
@Table(name = "business_onboarding")
@Getter
@Setter
@NoArgsConstructor
public class BusinessOnboarding {

    @Id
    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "services_checked_at")
    private Instant servicesCheckedAt;

    @Column(name = "resources_checked_at")
    private Instant resourcesCheckedAt;

    @Column(name = "hours_checked_at")
    private Instant hoursCheckedAt;

    @Column(name = "link_shared_at")
    private Instant linkSharedAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BusinessOnboarding(Long businessId, Instant createdAt) {
        this.businessId = businessId;
        this.createdAt = createdAt;
    }
}
