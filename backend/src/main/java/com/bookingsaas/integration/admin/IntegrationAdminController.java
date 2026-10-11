package com.bookingsaas.integration.admin;

import com.bookingsaas.integration.IntegrationProvider;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.ChannelView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.CreateTokenRequest;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.CreatedToken;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.N8nView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.Overview;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.TestResult;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.TokenView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.UpdateChannelRequest;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.UpdateN8nRequest;
import com.bookingsaas.common.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/admin/integrations")
@Tag(name = "Admin · Integrations")
public class IntegrationAdminController {

    private final IntegrationAdminService service;

    public IntegrationAdminController(IntegrationAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Overview overview() {
        return service.overview();
    }

    @PutMapping("/n8n")
    public N8nView updateN8n(@Valid @RequestBody UpdateN8nRequest request) {
        return service.updateN8n(request);
    }

    @PostMapping("/n8n/rotate-secret")
    public N8nView rotateSecret() {
        return service.rotateSigningSecret();
    }

    @PostMapping("/n8n/test")
    public TestResult test() {
        return service.sendTest();
    }

    @PutMapping("/channels/{provider}")
    public ChannelView updateChannel(@PathVariable String provider, @Valid @RequestBody UpdateChannelRequest request) {
        IntegrationProvider parsed;
        try {
            parsed = IntegrationProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.notFound("Unknown channel " + provider);
        }
        return service.updateChannel(parsed, request);
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedToken createToken(@Valid @RequestBody CreateTokenRequest request) {
        return service.createToken(request);
    }

    @DeleteMapping("/tokens/{tokenId}")
    public TokenView revokeToken(@PathVariable Long tokenId) {
        return service.revokeToken(tokenId);
    }
}
