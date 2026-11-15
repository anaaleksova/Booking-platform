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

    private JsonNode get(String path, Map<String, String> query) {
        String url = "https://graph.facebook.com/" + settings.graphVersion() + "/" + path + "?"
                + query.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
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
