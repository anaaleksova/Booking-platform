package com.bookingsaas.integration.meta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * The platform's Meta app. One app serves every business: each owner signs in
 * through it and grants it access to their own Page.
 */
@Component
public class MetaSettings {

    private final String appId;
    private final String appSecret;
    private final String graphVersion;
    private final String loginConfigId;
    private final List<String> scopes;
    private final String frontendUrl;
    private final String redirectUri;

    public MetaSettings(@Value("${app.meta.app-id:}") String appId,
                        @Value("${app.meta.app-secret:}") String appSecret,
                        @Value("${app.meta.graph-version:v21.0}") String graphVersion,
                        @Value("${app.meta.login-config-id:}") String loginConfigId,
                        @Value("${app.meta.scopes:}") String scopes,
                        @Value("${app.meta.redirect-uri:}") String redirectUri,
                        @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.appId = appId.trim();
        this.appSecret = appSecret.trim();
        this.graphVersion = graphVersion.isBlank() ? "v21.0" : graphVersion.trim();
        this.loginConfigId = loginConfigId.trim();
        this.scopes = Arrays.stream(scopes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.redirectUri = redirectUri.trim();
    }

    public boolean hasApp() {
        return !appId.isEmpty() && !appSecret.isEmpty();
    }

    public String appId() {
        return appId;
    }

    public String appSecret() {
        return appSecret;
    }

    public String graphVersion() {
        return graphVersion;
    }

    public String loginConfigId() {
        return loginConfigId;
    }

    public List<String> scopes() {
        return scopes;
    }

    /**
     * Where Meta sends the owner back. By default the Integrations page itself,
     * which hands the code to the API. A Live Meta app only accepts https
     * addresses, so a local setup sets {@code META_REDIRECT_URI} to
     * {@code https://<tunnel>/api/public/meta/return}, which forwards to the page.
     */
    public String redirectUri() {
        return redirectUri.isEmpty() ? integrationsPage() : redirectUri;
    }

    /** The Integrations page, which finishes the connection. */
    public String integrationsPage() {
        return frontendUrl + "/admin/integrations";
    }
}
