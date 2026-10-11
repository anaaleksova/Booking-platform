package com.bookingsaas.integration.identity;

import com.bookingsaas.common.domain.Auditable;
import com.bookingsaas.integration.ChannelProvider;
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
import lombok.Setter;

/**
 * "This Instagram/Facebook user is that customer of this business." Lets a
 * returning social user be recognised without asking for their details again.
 */
@Entity
@Table(name = "customer_external_identity")
@Getter
@Setter
@NoArgsConstructor
public class CustomerExternalIdentity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelProvider provider;

    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @Column(name = "display_name", length = 160)
    private String displayName;
}
