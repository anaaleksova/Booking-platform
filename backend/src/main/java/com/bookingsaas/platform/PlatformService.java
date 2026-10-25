package com.bookingsaas.platform;

import com.bookingsaas.auth.AuthService;
import com.bookingsaas.auth.User;
import com.bookingsaas.auth.UserRepository;
import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessRepository;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.common.security.PlatformAdmins;
import com.bookingsaas.platform.PlatformDtos.AccountRow;
import com.bookingsaas.platform.PlatformDtos.ActivityRow;
import com.bookingsaas.platform.PlatformDtos.BusinessRow;
import com.bookingsaas.platform.PlatformDtos.Overview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The platform area: every business and account, for the people who run the
 * platform. Access is checked once for the whole API by
 * {@code PlatformAccessInterceptor}; everything that reaches into a business
 * or takes something offline is written to the activity log.
 */
@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    private final PlatformRepository repository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final PlatformAdmins platformAdmins;
    private final CurrentTenant currentTenant;

    public PlatformService(PlatformRepository repository,
                           BusinessRepository businessRepository,
                           UserRepository userRepository,
                           AuthService authService,
                           PlatformAdmins platformAdmins,
                           CurrentTenant currentTenant) {
        this.repository = repository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.platformAdmins = platformAdmins;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return repository.overview();
    }

    @Transactional(readOnly = true)
    public List<BusinessRow> businesses(String query) {
        return repository.businesses(query);
    }

    @Transactional(readOnly = true)
    public List<AccountRow> accounts(String query) {
        return repository.accounts(query).stream()
                .map(row -> new AccountRow(row.id(), row.email(), row.fullName(), row.active(), row.createdAt(),
                        row.roles(), row.customerAt(), platformAdmins.isPlatformAdmin(row.email())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityRow> activity() {
        return repository.activity();
    }

    /**
     * A session in the business's admin with owner rights. Logged: it opens
     * the business's customers and their personal data.
     */
    @Transactional
    public AuthResponse enterBusiness(Long businessId) {
        Business business = requireBusiness(businessId);
        AuthenticatedUser admin = currentTenant.user();
        record(admin, "ENTERED_BUSINESS", "BUSINESS", business.getId(), business.getName());
        log.info("Platform admin user id={} entered business id={}", admin.userId(), business.getId());
        return authService.sessionFor(admin.userId(), business.getId());
    }

    /** Back to the administrator's own session: their own business, if they have one. */
    @Transactional(readOnly = true)
    public AuthResponse exitBusiness() {
        return authService.sessionFor(currentTenant.user().userId(), null);
    }

    /**
     * Takes the booking page offline (or back online). The owner and staff lose
     * the admin, and integration tokens stop working, while it is off; nothing is deleted.
     */
    @Transactional
    public BusinessRow setBusinessActive(Long businessId, boolean active) {
        Business business = requireBusiness(businessId);
        if (business.isActive() != active) {
            business.setActive(active);
            businessRepository.save(business);
            record(currentTenant.user(), active ? "BUSINESS_REACTIVATED" : "BUSINESS_DEACTIVATED", "BUSINESS",
                    business.getId(), business.getName());
        }
        return repository.businesses(business.getSlug()).stream()
                .filter(row -> row.id().equals(business.getId()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Stops (or restores) sign-in for an account; its sessions end at once.
     * Never the administrator's own account, nor another platform administrator's.
     */
    @Transactional
    public void setAccountActive(Long userId, boolean active) {
        AuthenticatedUser admin = currentTenant.user();
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Account", userId));
        if (user.getId().equals(admin.userId()) || platformAdmins.isPlatformAdmin(user.getEmail())) {
            throw ApiException.of(ErrorCode.ACCESS_DENIED, "A platform administrator's account cannot be "
                    + "deactivated here. Remove it from PLATFORM_ADMIN_EMAILS first.");
        }
        if (user.isActive() != active) {
            user.setActive(active);
            userRepository.save(user);
            record(admin, active ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED", "ACCOUNT", user.getId(),
                    user.getEmail());
        }
    }

    private Business requireBusiness(Long businessId) {
        return businessRepository.findById(businessId).orElseThrow(() -> ApiException.notFound("Business", businessId));
    }

    private void record(AuthenticatedUser admin, String action, String targetType, Long targetId, String targetName) {
        repository.recordActivity(admin.userId(), admin.email(), action, targetType, targetId, targetName);
    }
}
