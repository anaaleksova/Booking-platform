package com.bookingsaas.platform;

import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.platform.PlatformDtos.AccountRow;
import com.bookingsaas.platform.PlatformDtos.ActiveRequest;
import com.bookingsaas.platform.PlatformDtos.ActivityRow;
import com.bookingsaas.platform.PlatformDtos.BusinessRow;
import com.bookingsaas.platform.PlatformDtos.Overview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The platform area. Platform administrators only (see {@code PlatformAccessInterceptor}). */
@RestController
@RequestMapping("/api/platform")
@Tag(name = "Platform")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return platformService.overview();
    }

    @GetMapping("/businesses")
    public List<BusinessRow> businesses(@RequestParam(defaultValue = "") String query) {
        return platformService.businesses(query);
    }

    @PostMapping("/businesses/{id}/enter")
    @Operation(summary = "Open the business's admin with owner rights (logged)")
    public AuthResponse enterBusiness(@PathVariable Long id) {
        return platformService.enterBusiness(id);
    }

    @PostMapping("/exit")
    @Operation(summary = "Leave a business entered as platform administrator")
    public AuthResponse exitBusiness() {
        return platformService.exitBusiness();
    }

    @PutMapping("/businesses/{id}/active")
    public BusinessRow setBusinessActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return platformService.setBusinessActive(id, request.active());
    }

    @GetMapping("/accounts")
    public List<AccountRow> accounts(@RequestParam(defaultValue = "") String query) {
        return platformService.accounts(query);
    }

    @PutMapping("/accounts/{id}/active")
    public ResponseEntity<Void> setAccountActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        platformService.setAccountActive(id, request.active());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activity")
    public List<ActivityRow> activity() {
        return platformService.activity();
    }
}
