package com.bookingsaas.integration.meta;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The https address Meta may send the owner back to when the Integrations
 * page itself cannot be registered (a Live app refuses http://localhost). It
 * only forwards Meta's parameters to the page, which finishes the connection
 * with the owner's session — so nothing is done here, and the target is fixed.
 */
@Hidden
@RestController
public class MetaReturnController {

    private final MetaSettings settings;

    public MetaReturnController(MetaSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/api/public/meta/return")
    public ResponseEntity<Void> backToIntegrations(@RequestParam(required = false) String code,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(required = false) String error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromHttpUrl(settings.integrationsPage());
        if (code != null) {
            target.queryParam("code", code);
        }
        if (state != null) {
            target.queryParam("state", state);
        }
        if (error != null) {
            target.queryParam("error", error);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target.encode().build().toUriString())
                .build();
    }
}
