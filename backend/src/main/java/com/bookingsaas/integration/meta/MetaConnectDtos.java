package com.bookingsaas.integration.meta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shapes of "Connect with Facebook" on the Integrations page. */
public final class MetaConnectDtos {

    private MetaConnectDtos() {
    }

    /**
     * @param available the platform's Meta app is configured, so the Connect button works
     */
    public record MetaView(boolean available, boolean connected, String pageId, String pageName,
                           String instagramAccountId, String instagramUsername, Instant connectedAt) {

        static MetaView notConnected(boolean available) {
            return new MetaView(available, false, null, null, null, null, null);
        }
    }

    /** @param authorizeUrl Meta's login dialog; the browser goes there */
    public record StartResponse(String authorizeUrl) {
    }

    /** What Meta put on the redirect back to the Integrations page. */
    public record CompleteRequest(@NotBlank @Size(max = 2000) String code, @NotBlank @Size(max = 500) String state) {
    }

    /**
     * Either the business is connected, or the owner manages several Pages and
     * picks one with {@code sessionId}.
     */
    public record CompleteResponse(boolean needsPageChoice, MetaView connection, UUID sessionId,
                                   List<PageChoice> pages) {
    }

    /** @param connectedElsewhere already connected to another business; it cannot be picked */
    public record PageChoice(String id, String name, String instagramUsername, boolean connectedElsewhere) {
    }

    public record ChoosePageRequest(@NotNull UUID sessionId, @NotBlank @Size(max = 64) String pageId) {
    }
}
