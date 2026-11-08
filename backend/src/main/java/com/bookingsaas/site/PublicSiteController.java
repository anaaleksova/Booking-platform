package com.bookingsaas.site;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.site.dto.SiteDtos.PublicSiteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The published homepage. Never the draft: an owner's unfinished edits are not public. */
@RestController
@RequestMapping("/api/public/businesses/{slug}/site")
@Tag(name = "Public · Customer booking")
public class PublicSiteController {

    private final BusinessService businessService;
    private final SiteService siteService;

    public PublicSiteController(BusinessService businessService, SiteService siteService) {
        this.businessService = businessService;
        this.siteService = siteService;
    }

    @GetMapping
    @Operation(summary = "The business's published homepage, with the team and gallery it shows")
    public PublicSiteResponse site(@PathVariable String slug) {
        return siteService.publicSite(businessService.requireActiveBySlug(slug));
    }
}
