package com.bookingsaas.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** "This resource can serve this service" — e.g. Maria performs massages but not facials. */
@Entity
@Table(name = "resource_service")
@Getter
@Setter
@NoArgsConstructor
public class ResourceServiceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    public ResourceServiceLink(Long resourceId, Long serviceId) {
        this.resourceId = resourceId;
        this.serviceId = serviceId;
    }
}
