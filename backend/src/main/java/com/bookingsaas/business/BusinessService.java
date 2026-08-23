package com.bookingsaas.business;

import com.bookingsaas.business.dto.BusinessResponse;
import com.bookingsaas.business.dto.BusinessSettingsResponse;
import com.bookingsaas.business.dto.UpdateBusinessRequest;
import com.bookingsaas.business.dto.UpdateBusinessSettingsRequest;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.BusinessRole;
import com.bookingsaas.common.security.CurrentTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Tenant profile and booking rules.
 *
 * <p>Also the module's entry point for the rest of the monolith: other modules
 * ask here for a {@link Business} or its {@link BusinessSettings} rather than
 * touching the repositories directly.
 */
@Service
public class BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessService.class);

    private final BusinessRepository businessRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final BusinessUserRepository businessUserRepository;
    private final WorkingHoursService workingHoursService;
    private final CurrentTenant currentTenant;

    public BusinessService(BusinessRepository businessRepository,
                           BusinessSettingsRepository settingsRepository,
                           BusinessUserRepository businessUserRepository,
                           WorkingHoursService workingHoursService,
                           CurrentTenant currentTenant) {
        this.businessRepository = businessRepository;
        this.settingsRepository = settingsRepository;
        this.businessUserRepository = businessUserRepository;
        this.workingHoursService = workingHoursService;
        this.currentTenant = currentTenant;
    }

    // --- onboarding -------------------------------------------------------

    /**
     * Creates a tenant together with its owner membership and default rules.
     * Called from registration; a business is never left without an owner.
     */
    @Transactional
    public Business createBusiness(Long ownerUserId, String name, String requestedTimezone) {
        Business business = new Business();
        business.setName(name.trim());
        business.setSlug(Slugs.uniqueSlug(name, businessRepository::existsBySlug));
        business.setTimezone(validTimezone(requestedTimezone));
        Business saved = businessRepository.save(business);

        businessUserRepository.save(new BusinessUser(saved.getId(), ownerUserId, BusinessRole.BUSINESS_OWNER));
        settingsRepository.save(new BusinessSettings(saved.getId()));
        // A business with no opening hours would have no availability at all,
        // so onboarding starts from a weekday default the owner can edit.
        workingHoursService.applyDefaultWeek(saved.getId());

        log.info("Business created id={} slug={} owner={}", saved.getId(), saved.getSlug(), ownerUserId);
        return saved;
    }

    // --- lookups used by the other modules ---------------------------------

    @Transactional(readOnly = true)
    public Business requireById(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> ApiException.notFound("Business", businessId));
    }

    /** Public booking entry point: only an active business is reachable. */
    @Transactional(readOnly = true)
    public Business requireActiveBySlug(String slug) {
        return businessRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> ApiException.notFound("No business is published at /book/" + slug));
    }

    /**
     * Settings always exist for a live tenant, but a row can be missing for a
     * business created before the settings table — fall back to the defaults
     * rather than failing an availability request.
     */
    @Transactional(readOnly = true)
    public BusinessSettings settingsOf(Long businessId) {
        return settingsRepository.findByBusinessId(businessId)
                .orElseGet(() -> new BusinessSettings(businessId));
    }

    public ZoneId zoneOf(Long businessId) {
        return requireById(businessId).zoneId();
    }

    // --- dashboard API -----------------------------------------------------

    @Transactional(readOnly = true)
    public BusinessResponse currentBusiness() {
        return BusinessResponse.from(requireById(currentTenant.businessId()));
    }

    @Transactional
    public BusinessResponse updateCurrentBusiness(UpdateBusinessRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Business business = requireById(businessId);

        if (!business.getSlug().equals(request.slug())
                && businessRepository.findBySlug(request.slug()).filter(b -> !b.getId().equals(businessId)).isPresent()) {
            throw ApiException.of(ErrorCode.SLUG_ALREADY_TAKEN,
                    "The address /book/" + request.slug() + " is already in use.");
        }

        business.setName(request.name().trim());
        business.setSlug(request.slug());
        business.setDescription(trimToNull(request.description()));
        business.setLogoUrl(trimToNull(request.logoUrl()));
        business.setPhone(trimToNull(request.phone()));
        business.setEmail(trimToNull(request.email()));
        business.setAddress(trimToNull(request.address()));
        business.setTimezone(validTimezone(request.timezone()));

        return BusinessResponse.from(businessRepository.save(business));
    }

    @Transactional(readOnly = true)
    public BusinessSettingsResponse currentSettings() {
        return BusinessSettingsResponse.from(settingsOf(currentTenant.businessId()));
    }

    @Transactional
    public BusinessSettingsResponse updateCurrentSettings(UpdateBusinessSettingsRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BusinessSettings settings = settingsRepository.findByBusinessId(businessId)
                .orElseGet(() -> new BusinessSettings(businessId));

        settings.setMinimumBookingNoticeMinutes(request.minimumBookingNoticeMinutes());
        settings.setMaximumBookingAdvanceDays(request.maximumBookingAdvanceDays());
        settings.setSlotIntervalMinutes(request.slotIntervalMinutes());
        settings.setAllowCustomerCancellation(request.allowCustomerCancellation());
        settings.setCancellationDeadlineHours(request.cancellationDeadlineHours());
        settings.setDefaultAppointmentDurationMinutes(request.defaultAppointmentDurationMinutes());
        settings.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        settings.setReminderNotificationsEnabled(request.reminderNotificationsEnabled());
        settings.setReminderLeadHours(request.reminderLeadHours());

        return BusinessSettingsResponse.from(settingsRepository.save(settings));
    }

    private static String validTimezone(String timezone) {
        String candidate = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        try {
            ZoneId.of(candidate);
        } catch (DateTimeException ex) {
            throw ApiException.validation("'" + candidate + "' is not a valid IANA timezone, e.g. Europe/Skopje.");
        }
        return candidate;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
