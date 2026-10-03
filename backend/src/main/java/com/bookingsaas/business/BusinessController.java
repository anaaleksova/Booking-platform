package com.bookingsaas.business;

import com.bookingsaas.business.dto.BusinessResponse;
import com.bookingsaas.business.dto.BusinessSettingsResponse;
import com.bookingsaas.business.dto.UpdateBusinessRequest;
import com.bookingsaas.business.dto.UpdateBusinessSettingsRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenant acts on <em>its own</em> business only — there is deliberately no
 * {@code /api/admin/businesses/{id}} route, so there is no id for a client to tamper
 * with.
 */
@RestController
@RequestMapping("/api/admin/business")
@Tag(name = "Admin · Business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    public BusinessResponse current() {
        return businessService.currentBusiness();
    }

    @PutMapping
    public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.updateCurrentBusiness(request);
    }

    @GetMapping("/settings")
    public BusinessSettingsResponse settings() {
        return businessService.currentSettings();
    }

    @PutMapping("/settings")
    public BusinessSettingsResponse updateSettings(@Valid @RequestBody UpdateBusinessSettingsRequest request) {
        return businessService.updateCurrentSettings(request);
    }
}
