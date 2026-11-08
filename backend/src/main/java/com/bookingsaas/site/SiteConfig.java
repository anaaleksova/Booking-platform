package com.bookingsaas.site;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

/**
 * A business's homepage, twice: the draft the owner edits and the published
 * copy customers see. Both are {@code SiteDocument} JSON, held here as text
 * and bound by {@link SiteService}.
 *
 * <p>A business without a row has never opened the builder: its page is the
 * default one, built from its profile, until the first save.
 */
@Entity
@Table(name = "site_config")
@Getter
@Setter
@NoArgsConstructor
public class SiteConfig extends Auditable {

    @Id
    @Column(name = "business_id")
    private Long businessId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String draft;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String published;

    /**
     * Sent back with every save, so a save from a stale editor tab is refused
     * instead of overwriting. Null until the row is first stored.
     */
    @Version
    @Column(name = "draft_version", nullable = false)
    private Integer draftVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    public SiteConfig(Long businessId, String document) {
        this.businessId = businessId;
        this.draft = document;
        this.published = document;
    }
}
