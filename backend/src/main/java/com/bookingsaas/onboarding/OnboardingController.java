package com.bookingsaas.onboarding;

import com.bookingsaas.onboarding.dto.OnboardingResponse;
import com.bookingsaas.onboarding.dto.OnboardingResponse.StepKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The owner's "Getting started" checklist. Owner-only, like everything staff are not explicitly allowed. */
@RestController
@RequestMapping("/api/admin/onboarding")
@Tag(name = "Admin · Onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    @Operation(summary = "The checklist; 204 for a business that has none (created before templates)")
    public ResponseEntity<OnboardingResponse> current() {
        return onboardingService.current()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/steps/{key}")
    @Operation(summary = "Ticks a step: \"Looks good\", or the booking link was copied")
    public OnboardingResponse markDone(@PathVariable StepKey key) {
        return onboardingService.markDone(key);
    }

    @PostMapping("/dismiss")
    @Operation(summary = "Hides the checklist")
    public OnboardingResponse dismiss() {
        return onboardingService.dismiss();
    }
}
