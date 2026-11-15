package com.bookingsaas.event.share;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Shapes of "Share on Facebook / Instagram" for an event. */
public final class EventShareDtos {

    private EventShareDtos() {
    }

    /**
     * What the share dialog needs.
     *
     * @param facebookUnavailable  why Facebook cannot be chosen, or null when it can
     * @param instagramUnavailable why Instagram cannot be chosen, or null when it can
     * @param suggestedCaption     a starting text the owner edits
     */
    public record SharePreview(String pageName, String instagramUsername, String facebookUnavailable,
                               String instagramUnavailable, String suggestedCaption, List<PostView> posts) {
    }

    /** Instagram allows 2,200 characters, the stricter of the two. */
    public record ShareRequest(boolean facebook, boolean instagram, @NotBlank @Size(max = 2200) String caption) {
    }

    /** @param error Meta's reason, in words the owner can act on; null when posted */
    public record PostView(String platform, String status, String permalink, String error, Instant createdAt) {

        static PostView of(EventSocialPost post) {
            return new PostView(post.getPlatform().name(), post.getStatus().name(), post.getPermalink(),
                    post.getError(), post.getCreatedAt());
        }
    }

    public record ShareResult(List<PostView> posts) {
    }
}
