package com.bookingsaas.integration.meta;

import java.util.List;

/** The few Graph API calls "Connect with Facebook" needs. */
public interface MetaGraphClient {

    /** Swaps the code from the login dialog for a (short-lived) user token. */
    String exchangeCode(String code, String redirectUri);

    /** A 60-day user token; Page tokens read with it do not expire. */
    String longLivedUserToken(String shortLivedToken);

    /** The Pages the person granted the app, each with its own access token. */
    List<MetaPage> pages(String userToken);

    /** A photo post on the Page; Meta fetches the image from {@code imageUrl}. */
    PublishedPost postPagePhoto(String pageId, String pageToken, String imageUrl, String caption);

    /** A text-only post on the Page. */
    PublishedPost postPageText(String pageId, String pageToken, String message);

    /**
     * An image post on the Instagram account: a media container, waited on
     * until Meta has fetched the image, then published. Instagram takes JPEG only.
     */
    PublishedPost postInstagramImage(String instagramAccountId, String pageToken, String imageUrl, String caption);

    /** @param permalink the public link, when Meta returned one */
    record PublishedPost(String id, String permalink) {
    }

    /**
     * @param instagramAccountId the linked Instagram professional account, or null
     */
    record MetaPage(String id, String name, String accessToken, String instagramAccountId,
                    String instagramUsername) {
    }

    /** Meta refused or could not be reached; the message is safe to show the owner. */
    class MetaGraphException extends RuntimeException {
        public MetaGraphException(String message) {
            super(message);
        }
    }
}
