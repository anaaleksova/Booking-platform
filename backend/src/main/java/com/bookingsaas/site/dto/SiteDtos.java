package com.bookingsaas.site.dto;

import com.bookingsaas.site.model.SiteDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** What the homepage builder API sends and receives. */
public final class SiteDtos {

    private SiteDtos() {
    }

    /**
     * The owner's view: the draft being edited next to what customers see now.
     *
     * @param draftVersion send it back with the next save
     * @param publishedAt  null while the business has never published
     */
    public record SiteResponse(SiteDocument draft, SiteDocument published, int draftVersion, Instant publishedAt,
                               boolean hasUnpublishedChanges) {
    }

    /** @param draftVersion the version the edit started from, from the last {@link SiteResponse} */
    public record SaveDraftRequest(@NotNull Integer draftVersion, @NotNull @Valid SiteDocument document) {
    }

    /**
     * The published homepage, with the live data the business picked for it
     * that has no public endpoint of its own.
     *
     * @param team    the employees a team section shows, and only those
     * @param gallery the business's gallery photos, for a gallery section that shows them
     */
    public record PublicSiteResponse(SiteDocument site, List<TeamMember> team, List<Photo> gallery) {
    }

    public record TeamMember(Long id, String name, String imageUrl) {
    }

    public record Photo(String url, String caption) {
    }
}
