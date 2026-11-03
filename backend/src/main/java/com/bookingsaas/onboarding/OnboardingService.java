package com.bookingsaas.onboarding;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.domain.SetupAreaEdited;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.onboarding.dto.OnboardingResponse;
import com.bookingsaas.onboarding.dto.OnboardingResponse.Step;
import com.bookingsaas.onboarding.dto.OnboardingResponse.StepKey;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The "Getting started" checklist of a business created from a template.
 * Steps about the setup tick themselves when the owner saves that part of it
 * ({@link SetupAreaEdited}) or says it looks good; contact details and a
 * photo are read from the business itself.
 */
@Service
public class OnboardingService {

    private final BusinessOnboardingRepository repository;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public OnboardingService(BusinessOnboardingRepository repository,
                             BusinessService businessService,
                             CurrentTenant currentTenant,
                             Clock clock) {
        this.repository = repository;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    /** Empty for a business with no checklist (created before templates). */
    @Transactional(readOnly = true)
    public Optional<OnboardingResponse> current() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        return repository.findById(businessId).map(onboarding -> toResponse(onboarding,
                businessService.requireById(businessId)));
    }

    @Transactional
    public OnboardingResponse markDone(StepKey key) {
        currentTenant.requireOwner();
        if (!key.markable()) {
            throw ApiException.validation("This step is done by filling in the business's details.");
        }
        BusinessOnboarding onboarding = require();
        stamp(onboarding, key);
        return toResponse(repository.save(onboarding), businessService.requireById(onboarding.getBusinessId()));
    }

    @Transactional
    public OnboardingResponse dismiss() {
        currentTenant.requireOwner();
        BusinessOnboarding onboarding = require();
        if (onboarding.getDismissedAt() == null) {
            onboarding.setDismissedAt(clock.instant());
        }
        return toResponse(repository.save(onboarding), businessService.requireById(onboarding.getBusinessId()));
    }

    @EventListener
    public void onSetupAreaEdited(SetupAreaEdited event) {
        repository.findById(event.businessId()).ifPresent(onboarding -> {
            stamp(onboarding, switch (event.area()) {
                case SERVICES -> StepKey.SERVICES;
                case RESOURCES -> StepKey.RESOURCES;
                case HOURS -> StepKey.HOURS;
            });
            repository.save(onboarding);
        });
    }

    // --- internals ---------------------------------------------------------

    private BusinessOnboarding require() {
        return repository.findById(currentTenant.businessId())
                .orElseThrow(() -> ApiException.notFound("This business has no getting-started checklist."));
    }

    /** Records when a step was first done; doing it again changes nothing. */
    private void stamp(BusinessOnboarding onboarding, StepKey key) {
        Instant now = clock.instant();
        switch (key) {
            case SERVICES -> onboarding.setServicesCheckedAt(first(onboarding.getServicesCheckedAt(), now));
            case RESOURCES -> onboarding.setResourcesCheckedAt(first(onboarding.getResourcesCheckedAt(), now));
            case HOURS -> onboarding.setHoursCheckedAt(first(onboarding.getHoursCheckedAt(), now));
            case SHARE -> onboarding.setLinkSharedAt(first(onboarding.getLinkSharedAt(), now));
            case CONTACT, PHOTO -> throw new IllegalArgumentException(key + " is not recorded");
        }
    }

    private static Instant first(Instant existing, Instant now) {
        return existing != null ? existing : now;
    }

    private static OnboardingResponse toResponse(BusinessOnboarding onboarding, Business business) {
        List<Step> steps = List.of(
                new Step(StepKey.SERVICES, onboarding.getServicesCheckedAt() != null),
                new Step(StepKey.RESOURCES, onboarding.getResourcesCheckedAt() != null),
                new Step(StepKey.HOURS, onboarding.getHoursCheckedAt() != null),
                new Step(StepKey.CONTACT, present(business.getAddress()) && present(business.getPhone())),
                new Step(StepKey.PHOTO, business.getLogoMediaId() != null || business.getCoverMediaId() != null),
                new Step(StepKey.SHARE, onboarding.getLinkSharedAt() != null));
        return new OnboardingResponse(business.getBusinessType(), business.getSlug(), steps,
                onboarding.getDismissedAt() != null);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
