package com.bookingsaas.analytics;

import com.bookingsaas.analytics.AnalyticsRepository.CustomerMixRow;
import com.bookingsaas.analytics.AnalyticsRepository.ReservationDayRow;
import com.bookingsaas.analytics.AnalyticsRepository.ResourceUsageRow;
import com.bookingsaas.analytics.AnalyticsRepository.ServiceRow;
import com.bookingsaas.analytics.AnalyticsRepository.TopCustomerRow;
import com.bookingsaas.analytics.dto.AnalyticsDtos.CustomerReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ReservationPoint;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ReservationReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ResourceUtilization;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ServiceStat;
import com.bookingsaas.analytics.dto.AnalyticsDtos.TopCustomer;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerRepository;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.resource.ResourceType;
import com.bookingsaas.resource.ResourceTypeService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservation, service, resource and customer statistics — all computed from
 * the database, owner-only, and scoped to the caller's business.
 */
@Service
public class AnalyticsService {

    private static final int DEFAULT_DAYS = 30;

    private final AnalyticsRepository repository;
    private final BusinessService businessService;
    private final OpeningHoursService openingHoursService;
    private final ServiceCatalogService serviceCatalog;
    private final ResourceService resourceService;
    private final ResourceTypeService resourceTypeService;
    private final CustomerRepository customerRepository;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository,
                            BusinessService businessService,
                            OpeningHoursService openingHoursService,
                            ServiceCatalogService serviceCatalog,
                            ResourceService resourceService,
                            ResourceTypeService resourceTypeService,
                            CustomerRepository customerRepository,
                            CurrentTenant currentTenant,
                            Clock clock) {
        this.repository = repository;
        this.businessService = businessService;
        this.openingHoursService = openingHoursService;
        this.serviceCatalog = serviceCatalog;
        this.resourceService = resourceService;
        this.resourceTypeService = resourceTypeService;
        this.customerRepository = customerRepository;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationReport reservations(LocalDate from, LocalDate to) {
        Scope scope = scope(from, to);
        Map<LocalDate, ReservationDayRow> rows = repository.reservationsByDay(scope.businessId(),
                        scope.period().start(), scope.period().end(), scope.zone().getId())
                .stream().collect(Collectors.toMap(ReservationDayRow::day, Function.identity()));

        List<ReservationPoint> series = new ArrayList<>();
        long total = 0;
        long cancelled = 0;
        for (LocalDate day = scope.period().from(); !day.isAfter(scope.period().to()); day = day.plusDays(1)) {
            ReservationDayRow row = rows.get(day);
            ReservationPoint point = row == null
                    ? new ReservationPoint(day, 0, 0, 0, 0, 0, 0)
                    : new ReservationPoint(day, row.pending() + row.confirmed() + row.completed(), row.pending(),
                            row.confirmed(), row.completed(), row.cancelled(), row.participants());
            total += point.total();
            cancelled += point.cancelled();
            series.add(point);
        }
        return new ReservationReport(scope.period().from(), scope.period().to(), total, cancelled, series);
    }

    /** Most booked services first; ties broken by revenue. */
    @Transactional(readOnly = true)
    public List<ServiceStat> services(LocalDate from, LocalDate to) {
        Scope scope = scope(from, to);
        List<ServiceRow> rows = repository.byService(scope.businessId(), scope.period().start(), scope.period().end());
        Map<Long, BookableService> services = serviceCatalog.byIds(scope.businessId(),
                        rows.stream().map(ServiceRow::serviceId).toList())
                .stream().collect(Collectors.toMap(BookableService::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    BookableService service = services.get(row.serviceId());
                    return new ServiceStat(row.serviceId(), service == null ? "Deleted service" : service.getName(),
                            service != null && service.isActive(), row.reservations(), row.participants(),
                            row.revenue());
                })
                .sorted(Comparator.comparingLong(ServiceStat::reservations).reversed()
                        .thenComparing(ServiceStat::revenue, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * Booked time against bookable time for every active resource.
     *
     * <p>Bookable time is each day's opening hours — narrowed to the
     * resource's own schedule where it has one — summed over the range.
     * Business-wide closures are not subtracted, so a closed holiday lowers
     * utilization slightly; for an MVP metric that is an acceptable bias.
     */
    @Transactional(readOnly = true)
    public List<ResourceUtilization> resources(LocalDate from, LocalDate to) {
        Scope scope = scope(from, to);
        Long businessId = scope.businessId();
        List<Resource> resources = resourceService.allActive(businessId);
        if (resources.isEmpty()) {
            return List.of();
        }
        Map<Long, ResourceUsageRow> usage = repository.resourceUsage(businessId, scope.period().start(),
                        scope.period().end())
                .stream().collect(Collectors.toMap(ResourceUsageRow::resourceId, Function.identity()));
        Map<Long, ResourceType> types = resourceTypeService.byIds(businessId,
                resources.stream().map(Resource::getResourceTypeId).distinct().toList());
        Map<Long, List<DayInterval>> ownHours = resourceService.hoursOf(businessId,
                resources.stream().map(Resource::getId).toList());
        List<DayInterval> openingHours = openingHoursService.intervalsOf(businessId);

        return resources.stream()
                .map(resource -> {
                    ResourceUsageRow row = usage.get(resource.getId());
                    long booked = row == null ? 0 : Math.round(row.bookedMinutes());
                    long available = availableMinutes(scope, openingHours, ownHours.get(resource.getId()));
                    ResourceType type = types.get(resource.getResourceTypeId());
                    return new ResourceUtilization(resource.getId(), resource.getName(), resource.getResourceTypeId(),
                            type == null ? null : type.getName(), row == null ? 0 : row.reservations(), booked,
                            available, available == 0 ? 0 : Math.min(1.0, (double) booked / available));
                })
                .sorted(Comparator.comparingDouble(ResourceUtilization::utilization).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerReport customers(LocalDate from, LocalDate to) {
        Scope scope = scope(from, to);
        Long businessId = scope.businessId();
        CustomerMixRow mix = repository.customerMix(businessId, scope.period().start(), scope.period().end());
        List<TopCustomerRow> top = repository.topCustomers(businessId, scope.period().start(), scope.period().end(), 5);
        Map<Long, Customer> customers = customerRepository.findByBusinessIdAndIdIn(businessId,
                        top.stream().map(TopCustomerRow::customerId).toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        return new CustomerReport(scope.period().from(), scope.period().to(), mix.active(), mix.newCustomers(),
                mix.returning(), top.stream()
                .map(row -> new TopCustomer(row.customerId(),
                        customers.containsKey(row.customerId()) ? customers.get(row.customerId()).fullName() : null,
                        row.reservations(), row.spent() == null ? BigDecimal.ZERO : row.spent()))
                .toList());
    }

    // --- internals ---------------------------------------------------------

    private record Scope(Long businessId, ZoneId zone, ReportingPeriod period) {
    }

    private Scope scope(LocalDate from, LocalDate to) {
        currentTenant.requireOwner();
        Business business = businessService.requireById(currentTenant.businessId());
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new Scope(business.getId(), zone,
                ReportingPeriod.of(from, to, today.minusDays(DEFAULT_DAYS - 1L), today, zone));
    }

    private static long availableMinutes(Scope scope, List<DayInterval> openingHours, List<DayInterval> own) {
        long minutes = 0;
        for (LocalDate day = scope.period().from(); !day.isAfter(scope.period().to()); day = day.plusDays(1)) {
            List<TimeRange> open = ranges(day, openingHours, scope.zone());
            if (own != null && !own.isEmpty()) {
                open = TimeRange.intersect(ranges(day, own, scope.zone()), open);
            }
            for (TimeRange range : open) {
                minutes += range.duration().toMinutes();
            }
        }
        return minutes;
    }

    private static List<TimeRange> ranges(LocalDate day, List<DayInterval> intervals, ZoneId zone) {
        List<TimeRange> ranges = new ArrayList<>();
        for (DayInterval interval : intervals) {
            if (interval.dayOfWeek() == day.getDayOfWeek().getValue()) {
                var start = day.atTime(interval.startTime()).atZone(zone).toInstant();
                var end = day.atTime(interval.endTime()).atZone(zone).toInstant();
                if (Duration.between(start, end).isPositive()) {
                    ranges.add(TimeRange.of(start, end));
                }
            }
        }
        return TimeRange.merge(ranges);
    }
}
