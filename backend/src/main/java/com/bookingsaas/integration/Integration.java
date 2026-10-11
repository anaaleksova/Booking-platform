package com.bookingsaas.integration;

import com.bookingsaas.common.domain.Auditable;
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
 * A business's connection to one provider.
 *
 * <p>For the MVP the n8n row holds the webhook events are posted to, and the
 * social rows hold the page/account id messages arrive for. OAuth-connected
 * channels will add their credentials in separate, encrypted storage — this
 * row only records that a connection exists.
 */
@Entity
@Table(name = "integration")
@Getter
@Setter
@NoArgsConstructor
public class Integration extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntegrationStatus status = IntegrationStatus.NOT_CONNECTED;

    @Column(name = "webhook_url", length = 1000)
    private String webhookUrl;

    @Column(name = "signing_secret", length = 128)
    private String signingSecret;

    @Column(name = "external_account_id", length = 128)
    private String externalAccountId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    public Integration(Long businessId, IntegrationProvider provider) {
        this.businessId = businessId;
        this.provider = provider;
    }

    public boolean isConnected() {
        return status == IntegrationStatus.CONNECTED;
    }
}
