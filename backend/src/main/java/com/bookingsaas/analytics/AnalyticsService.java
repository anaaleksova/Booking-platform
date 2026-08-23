package com.bookingsaas.analytics;

import com.bookingsaas.analytics.dto.AnalyticsOverviewResponse;
import com.bookingsaas.analytics.dto.CustomerAnalyticsResponse;
import com.bookingsaas.analytics.dto.ServiceRevenueResponse;
import com.bookingsaas.booking.Booking;
import com.bookingsaas.booking.BookingRepository;
import com.bookingsaas.booking.BookingStatus;
import com.bookingsaas.booking.CustomerAggregateRow;
import com.bookingsaas.booking.CustomerCancellationRow;
import com.bookingsaas.booking.CustomerServiceCountRow;
import com.bookingsaas.booking.ServiceRevenueRow;
import com.bookingsaas.booking.StatusAggregateRow;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerRepository;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.BookableServiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business-wide reporting: revenue, service performance and customer value.
 *
 * <p>Owner-only throughout — a staff member's own schedule is fine to show them
 * (see {@link com.bookingsaas.booking.DashboardService}), but the business's
 * revenue and customer spend are not.
 */
@Service
public class AnalyticsService {

    private final BookingRepository bookingRepository;
    private final BookableServiceRepository serviceRepository;
    private final CustomerRepository customerRepository;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public AnalyticsService(BookingRepository bookingRepository,
                            BookableServiceRepository serviceRepository,
                            CustomerRepository customerRepository,
                            BusinessService businessService,
                            CurrentTenant currentTenant,
                            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
        this.customerRepository = customerRepository;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(LocalDate from, LocalDate to) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);

        // Each window spans its whole period, including hours/days not yet
        // reached — an appointment booked for later this afternoon is part of
        // "today's revenue" already, the same way DashboardService treats it.
        LocalDate weekStartDate = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate monthStartDate = today.withDayOfMonth(1);
        LocalDate yearStartDate = today.withDayOfYear(1);

        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant weekStart = weekStartDate.atStartOfDay(zone).toInstant();
        Instant weekEnd = weekStartDate.plusDays(7).atStartOfDay(zone).toInstant();
        Instant monthStart = monthStartDate.atStartOfDay(zone).toInstant();
        Instant monthEnd = monthStartDate.plusMonths(1).atStartOfDay(zone).toInstant();
        Instant yearStart = yearStartDate.atStartOfDay(zone).toInstant();
        Instant yearEnd = yearStartDate.plusYears(1).atStartOfDay(zone).toInstant();

        BigDecimal revenueToday = bookingRepository.sumRevenue(businessId, todayStart, todayEnd, BookingStatus.CANCELLED);
        BigDecimal revenueThisWeek = bookingRepository.sumRevenue(businessId, weekStart, weekEnd, BookingStatus.CANCELLED);
        BigDecimal revenueThisMonth = bookingRepository.sumRevenue(businessId, monthStart, monthEnd, BookingStatus.CANCELLED);
        BigDecimal revenueThisYear = bookingRepository.sumRevenue(businessId, yearStart, yearEnd, BookingStatus.CANCELLED);

        LocalDate rangeFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate rangeTo = to != null ? to : today;
        Instant rangeFromInstant = rangeFrom.atStartOfDay(zone).toInstant();
        Instant rangeToInstant = rangeTo.plusDays(1).atStartOfDay(zone).toInstant();

        List<StatusAggregateRow> statusRows = bookingRepository.aggregateByStatus(
                businessId, rangeFromInstant, rangeToInstant);

        long appointmentCount = 0;
        long completedCount = 0;
        long cancelledCount = 0;
        long noShowCount = 0;
        BigDecimal rangeRevenue = BigDecimal.ZERO;
        long revenueCountedAppointments = 0;
        for (StatusAggregateRow row : statusRows) {
            appointmentCount += row.getCount();
            if (row.getStatus() == BookingStatus.COMPLETED) {
                completedCount = row.getCount();
            } else if (row.getStatus() == BookingStatus.CANCELLED) {
                cancelledCount = row.getCount();
            } else if (row.getStatus() == BookingStatus.NO_SHOW) {
                noShowCount = row.getCount();
            }
            if (row.getStatus() != BookingStatus.CANCELLED) {
                rangeRevenue = rangeRevenue.add(row.getRevenue());
                revenueCountedAppointments += row.getCount();
            }
        }

        BigDecimal averageBookingValue = average(rangeRevenue, revenueCountedAppointments);
        double cancellationRate = rate(cancelledCount, appointmentCount);
        double noShowRate = rate(noShowCount, appointmentCount);
        String currency = resolveCurrency(businessId);

        return new AnalyticsOverviewResponse(
                revenueToday, revenueThisWeek, revenueThisMonth, revenueThisYear, currency,
                rangeFrom, rangeTo, appointmentCount, completedCount, cancelledCount, noShowCount,
                averageBookingValue, cancellationRate, noShowRate);
    }

    @Transactional(readOnly = true)
    public List<ServiceRevenueResponse> revenueByService(LocalDate from, LocalDate to) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        Instant fromInstant = from != null ? from.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant toInstant = endOfRange(to, zone);

        List<ServiceRevenueRow> rows = bookingRepository.aggregateRevenueByService(
                businessId, fromInstant, toInstant, BookingStatus.CANCELLED);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> serviceIds = rows.stream().map(ServiceRevenueRow::getServiceId).distinct().toList();
        Map<Long, BookableService> services = serviceRepository.findByBusinessIdAndIdIn(businessId, serviceIds)
                .stream().collect(Collectors.toMap(BookableService::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    BookableService service = services.get(row.getServiceId());
                    return new ServiceRevenueResponse(
                            row.getServiceId(),
                            service == null ? "Deleted service" : service.getName(),
                            service != null && service.isActive(),
                            row.getAppointmentCount(),
                            row.getRevenue(),
                            row.getCurrency());
                })
                .sorted(Comparator.comparing(ServiceRevenueResponse::revenue).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CustomerAnalyticsResponse> customers(LocalDate from, LocalDate to, int page, int size) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        Instant fromInstant = from != null ? from.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant toInstant = endOfRange(to, zone);

        Page<CustomerAggregateRow> spendPage = bookingRepository.aggregateCustomerSpend(
                businessId, fromInstant, toInstant, BookingStatus.CANCELLED,
                PageRequest.of(page, Math.min(size, 100)));

        if (spendPage.isEmpty()) {
            return spendPage.map(row -> null);
        }

        List<Long> customerIds = spendPage.getContent().stream()
                .map(CustomerAggregateRow::getCustomerId).toList();

        Map<Long, Customer> customers = customerRepository.findByBusinessIdAndIdIn(businessId, customerIds)
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        Map<Long, Long> cancellations = bookingRepository.countCancellationsForCustomers(
                        businessId, customerIds, fromInstant, toInstant, BookingStatus.CANCELLED)
                .stream().collect(Collectors.toMap(CustomerCancellationRow::getCustomerId,
                        CustomerCancellationRow::getCancellationCount));

        Map<Long, Long> favouriteServiceIdByCustomer = bookingRepository.aggregateServiceCountsForCustomers(
                        businessId, customerIds, fromInstant, toInstant, BookingStatus.CANCELLED)
                .stream().collect(Collectors.groupingBy(CustomerServiceCountRow::getCustomerId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparingLong(CustomerServiceCountRow::getCount)),
                                best -> best.map(CustomerServiceCountRow::getServiceId).orElse(null))));

        List<Long> favouriteServiceIds = favouriteServiceIdByCustomer.values().stream()
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, BookableService> favouriteServices = favouriteServiceIds.isEmpty() ? Map.of()
                : serviceRepository.findByBusinessIdAndIdIn(businessId, favouriteServiceIds)
                        .stream().collect(Collectors.toMap(BookableService::getId, Function.identity()));

        return spendPage.map(row -> {
            Customer customer = customers.get(row.getCustomerId());
            Long favouriteServiceId = favouriteServiceIdByCustomer.get(row.getCustomerId());
            BookableService favouriteService = favouriteServiceId == null ? null
                    : favouriteServices.get(favouriteServiceId);

            return new CustomerAnalyticsResponse(
                    row.getCustomerId(),
                    customer == null ? null : customer.getName(),
                    row.getAppointmentCount(),
                    row.getTotalSpent(),
                    average(row.getTotalSpent(), row.getAppointmentCount()),
                    row.getLastAppointmentAt(),
                    favouriteServiceId,
                    favouriteService == null ? null : favouriteService.getName(),
                    cancellations.getOrDefault(row.getCustomerId(), 0L));
        });
    }

    /**
     * The end of the given calendar day, exclusive — or of "today" if no date
     * was given. Always day-aligned, never the current instant, so a booking
     * later today does not fall outside "up to today" just because the report
     * was pulled this morning.
     */
    private Instant endOfRange(LocalDate to, ZoneId zone) {
        LocalDate day = to != null ? to : LocalDate.ofInstant(clock.instant(), zone);
        return day.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private String resolveCurrency(Long businessId) {
        return bookingRepository.findFirstByBusinessIdAndCurrencyIsNotNullOrderByStartDateTimeDesc(businessId)
                .map(Booking::getCurrency)
                .orElse("EUR");
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0.0 : (double) part / whole;
    }
}
