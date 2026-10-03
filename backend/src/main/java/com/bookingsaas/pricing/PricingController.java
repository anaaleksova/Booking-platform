package com.bookingsaas.pricing;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.pricing.dto.QuoteRequest;
import com.bookingsaas.pricing.dto.QuoteResponse;
import com.bookingsaas.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The same price computation the customer pages use, for reservations taken in the admin panel. */
@RestController
@RequestMapping("/api/admin/pricing")
@Tag(name = "Admin · Reservations")
public class PricingController {

    private final PricingService pricingService;
    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final CurrentTenant currentTenant;

    public PricingController(PricingService pricingService, BusinessService businessService,
                             ServiceCatalogService serviceCatalog, CurrentTenant currentTenant) {
        this.pricingService = pricingService;
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.currentTenant = currentTenant;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        Business business = businessService.requireById(currentTenant.businessId());
        return QuoteResponse.from(pricingService.quote(business,
                serviceCatalog.requireForBusiness(business.getId(), request.serviceId()),
                request.participants(), request.discountCode()));
    }
}
