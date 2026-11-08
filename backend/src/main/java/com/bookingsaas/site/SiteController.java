package com.bookingsaas.site;

import com.bookingsaas.site.dto.SiteDtos.SaveDraftRequest;
import com.bookingsaas.site.dto.SiteDtos.SiteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The business's own homepage, as the owner edits it. Owner-only, like every admin route not opened to staff. */
@RestController
@RequestMapping("/api/admin/site")
@Tag(name = "Admin · Homepage")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public SiteResponse current() {
        return siteService.current();
    }

    @PutMapping("/draft")
    @Operation(summary = "Save the draft; customers keep seeing the published page")
    public SiteResponse saveDraft(@Valid @RequestBody SaveDraftRequest request) {
        return siteService.saveDraft(request);
    }

    @PostMapping("/publish")
    @Operation(summary = "Make the draft the page customers see")
    public SiteResponse publish() {
        return siteService.publish();
    }

    @PostMapping("/discard")
    @Operation(summary = "Throw the draft away and start again from the published page")
    public SiteResponse discard() {
        return siteService.discard();
    }
}
