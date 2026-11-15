package com.bookingsaas.integration.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HttpMetaGraphClient implements MetaGraphClient {

    private static final Logger log = LoggerFactory.getLogger(HttpMetaGraphClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** A person rarely manages more; the rest are ignored rather than paged through. */
    private static final int MAX_PAGES = 100;
    /** Instagram usually has the picture within a few seconds. */
    private static final int CONTAINER_CHECKS = 15;
    private static final Duration CONTAINER_WAIT = Duration.ofSeconds(1);

    private final MetaSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public HttpMetaGraphClient(MetaSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", settings.appId());
        query.put("client_secret", settings.appSecret());
        query.put("redirect_uri", redirectUri);
        query.put("code", code);
        return get("oauth/access_token", query).path("access_token").asText(null);
    }

    @Override
    public String longLivedUserToken(String shortLivedToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("grant_type", "fb_exchange_token");
        query.put("client_id", settings.appId());
        query.put("client_secret", settings.appSecret());
        query.put("fb_exchange_token", shortLivedToken);
        return get("oauth/access_token", query).path("access_token").asText(null);
    }

    @Override
    public List<MetaPage> pages(String userToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("fields", "id,name,access_token,instagram_business_account{id,username}");
        query.put("limit", String.valueOf(MAX_PAGES));
        query.put("access_token", userToken);
        List<MetaPage> pages = new ArrayList<>();
        for (JsonNode page : get("me/accounts", query).path("data")) {
            JsonNode instagram = page.path("instagram_business_account");
            pages.add(new MetaPage(page.path("id").asText(), page.path("name").asText(),
                    page.path("access_token").asText(null),
                    instagram.isMissingNode() ? null : instagram.path("id").asText(null),
                    instagram.isMissingNode() ? null : instagram.path("username").asText(null)));
        }
        return pages;
    }

    @Override
    public PublishedPost postPagePhoto(String pageId, String pageToken, String imageUrl, String caption) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("url", imageUrl);
        form.put("caption", caption);
        form.put("access_token", pageToken);
        JsonNode body = post(pageId + "/photos", form);
        // post_id is the post on the Page; id alone is the photo.
        String postId = body.path("post_id").asText(body.path("id").asText());
        return new PublishedPost(postId, "https://www.facebook.com/" + postId);
    }

    @Override
    public PublishedPost postPageText(String pageId, String pageToken, String message) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("message", message);
        form.put("access_token", pageToken);
        String postId = post(pageId + "/feed", form).path("id").asText();
        return new PublishedPost(postId, "https://www.facebook.com/" + postId);
    }

    @Override
    public PublishedPost postInstagramImage(String instagramAccountId, String pageToken, String imageUrl,
                                            String caption) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("image_url", imageUrl);
        form.put("caption", caption);
        form.put("access_token", pageToken);
        String container = post(instagramAccountId + "/media", form).path("id").asText();
        awaitContainer(container, pageToken);

        Map<String, String> publish = new LinkedHashMap<>();
        publish.put("creation_id", container);
        publish.put("access_token", pageToken);
        String mediaId = post(instagramAccountId + "/media_publish", publish).path("id").asText();

        Map<String, String> query = new LinkedHashMap<>();
        query.put("fields", "permalink");
        query.put("access_token", pageToken);
        String permalink = null;
        try {
            permalink = get(mediaId, query).path("permalink").asText(null);
        } catch (MetaGraphException ex) {
            // Published all the same; the link is a nicety.
        }
        return new PublishedPost(mediaId, permalink);
    }

    /** Meta fetches the image in the background; publishing before it is FINISHED fails. */
    private void awaitContainer(String container, String pageToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("fields", "status_code,status");
        query.put("access_token", pageToken);
        for (int attempt = 0; attempt < CONTAINER_CHECKS; attempt++) {
            JsonNode status = get(container, query);
            String code = status.path("status_code").asText("");
            if (code.equals("FINISHED")) {
                return;
            }
            if (code.equals("ERROR") || code.equals("EXPIRED")) {
                throw new MetaGraphException("Instagram could not use the picture: "
                        + status.path("status").asText(code));
            }
            try {
                Thread.sleep(CONTAINER_WAIT.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MetaGraphException("Interrupted while Instagram prepared the post.");
            }
        }
        throw new MetaGraphException("Instagram took too long to prepare the post. Please try again.");
    }

    private JsonNode get(String path, Map<String, String> query) {
        String url = graphUrl(path) + "?" + encode(query);
        return send(path, HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build());
    }

    private JsonNode post(String path, Map<String, String> form) {
        return send(path, HttpRequest.newBuilder(URI.create(graphUrl(path))).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build());
    }

    private String graphUrl(String path) {
        return "https://graph.facebook.com/" + settings.graphVersion() + "/" + path;
    }

    private static String encode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private JsonNode send(String path, HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() != 200 || body.has("error")) {
                String message = body.path("error").path("message").asText("HTTP " + response.statusCode());
                // The path only: the query holds the app secret and tokens.
                log.warn("Meta Graph {} failed: {}", path, message);
                throw new MetaGraphException("Facebook refused the request: " + message);
            }
            return body;
        } catch (IOException ex) {
            log.warn("Meta Graph {} unreachable: {}", path, ex.getMessage());
            throw new MetaGraphException("Could not reach Facebook. Please try again.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetaGraphException("Interrupted while talking to Facebook.");
        }
    }
}
