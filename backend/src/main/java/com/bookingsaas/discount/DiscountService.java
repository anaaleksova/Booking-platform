package com.bookingsaas.discount;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.discount.dto.DiscountRequest;
import com.bookingsaas.discount.dto.DiscountResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Promo-code management, validation and usage accounting. */
@Service
public class DiscountService {

    private final DiscountRepository repository;
    private final ServiceCatalogService serviceCatalog;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public DiscountService(DiscountRepository repository,
                           ServiceCatalogService serviceCatalog,
                           BusinessService businessService,
                           CurrentTenant currentTenant,
                           Clock clock) {
        this.repository = repository;
        this.serviceCatalog = serviceCatalog;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    // --- used by pricing and reservations ---------------------------------

    /**
     * Finds a code and checks every rule against it.
     *
     * @throws ApiException {@code DISCOUNT_INVALID} for an unknown code, or the
     *                      specific reason the code cannot be used
     */
    @Transactional(readOnly = true)
    public Discount requireApplicable(Long businessId, String code, Long serviceId, BigDecimal originalPrice) {
        Discount discount = repository.findByCode(businessId, code.trim())
                .orElseThrow(() -> ApiException.of(ErrorCode.DISCOUNT_INVALID, "This discount code is not valid."));
        DiscountPolicy.checkApplicable(discount, serviceId, originalPrice, today(businessId));
        return discount;
    }

    /** Takes one use of the discount; fails if another reservation just took the last one. */
    @Transactional
    public void redeem(Long discountId) {
        if (repository.redeem(discountId) == 0) {
            throw ApiException.of(ErrorCode.DISCOUNT_USAGE_LIMIT_REACHED,
                    "This discount code has reached its usage limit.");
        }
    }

    @Transactional
    public void release(Long discountId) {
        repository.release(discountId);
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DiscountResponse> list() {
        Long businessId = currentTenant.businessId();
        LocalDate today = today(businessId);
        return repository.findByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(discount -> DiscountResponse.from(discount, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public DiscountResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        return DiscountResponse.from(requireForBusiness(businessId, id), today(businessId));
    }

    @Transactional
    public DiscountResponse create(DiscountRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Discount discount = new Discount();
        discount.setBusinessId(businessId);
        apply(businessId, discount, request);
        return DiscountResponse.from(save(discount), today(businessId));
    }

    @Transactional
    public DiscountResponse update(Long id, DiscountRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Discount discount = requireForBusiness(businessId, id);
        if (request.maxUsage() != null && request.maxUsage() < discount.getUsageCount()) {
            throw ApiException.validation("The code has already been used " + discount.getUsageCount()
                    + " times; the maximum usage cannot be lower.");
        }
        apply(businessId, discount, request);
        return DiscountResponse.from(save(discount), today(businessId));
    }

    /** Reservations keep their price snapshot and code; the link is simply cleared. */
    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        repository.delete(requireForBusiness(currentTenant.businessId(), id));
    }

    // --- internals ---------------------------------------------------------

    private Discount requireForBusiness(Long businessId, Long id) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Discount", id));
    }

    private void apply(Long businessId, Discount discount, DiscountRequest request) {
        if (request.type() == DiscountType.PERCENTAGE && request.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.validation("A percentage discount cannot exceed 100%.");
        }
        if (request.startsOn() != null && request.endsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw ApiException.validation("The end date must not be before the start date.");
        }

        Set<Long> serviceIds = new HashSet<>();
        if (!request.appliesToAllServices()) {
            serviceIds = request.serviceIds() == null ? new HashSet<>()
                    : request.serviceIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            if (serviceIds.isEmpty()) {
                throw ApiException.validation("Select at least one service, or make the code apply to all services.");
            }
            Set<Long> owned = serviceCatalog.byIds(businessId, List.copyOf(serviceIds)).stream()
                    .map(BookableService::getId)
                    .collect(Collectors.toSet());
            if (!owned.equals(serviceIds)) {
                throw ApiException.validation("One or more selected services do not belong to this business.");
            }
        }

        discount.setName(request.name().trim());
        discount.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        discount.setType(request.type());
        discount.setValue(request.value());
        discount.setStartsOn(request.startsOn());
        discount.setEndsOn(request.endsOn());
        discount.setActive(request.active());
        discount.setMaxUsage(request.maxUsage());
        discount.setMinBookingValue(request.minBookingValue());
        discount.setAppliesToAllServices(request.appliesToAllServices());
        discount.getServiceIds().clear();
        discount.getServiceIds().addAll(serviceIds);
    }

    private Discount save(Discount discount) {
        try {
            Discount saved = repository.save(discount);
            repository.flush();
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.DUPLICATE_DISCOUNT_CODE,
                    "The code " + discount.getCode() + " is already in use.");
        }
    }

    private LocalDate today(Long businessId) {
        return LocalDate.now(clock.withZone(businessService.zoneOf(businessId)));
    }
}
