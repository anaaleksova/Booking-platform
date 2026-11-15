package com.bookingsaas.integration.meta;

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

/** The Facebook Page (and its Instagram account) a business connected by signing in with Meta. */
@Entity
@Table(name = "meta_connection")
@Getter
@Setter
@NoArgsConstructor
public class MetaConnection extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "page_id", nullable = false, length = 64)
    private String pageId;

    @Column(name = "page_name", nullable = false)
    private String pageName;

    @Column(name = "instagram_account_id", length = 64)
    private String instagramAccountId;

    @Column(name = "instagram_username")
    private String instagramUsername;

    /** Encrypted by {@link TokenCipher}; never leaves the backend through the admin API. */
    @Column(name = "page_token_encrypted", nullable = false, length = 2000)
    private String pageTokenEncrypted;

    @Column(name = "connected_by_user_id")
    private Long connectedByUserId;

    public MetaConnection(Long businessId) {
        this.businessId = businessId;
    }
}
