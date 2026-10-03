package com.bookingsaas.analytics;

import com.bookingsaas.analytics.dto.AnalyticsDtos.Dashboard;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenueSummary;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.reservation.ReservationQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * The KPI cards on the admin landing page.
 *
 * <p>"Today" means today <em>in the business timezone</em> — a spa in Skopje
 * and a restaurant in Chicago must each see their own day.
 */
@Service
public class DashboardService {

    private final RevenueService revenueService;
    private final AnalyticsRepository repository;
    private final BusinessService businessService;
    private final CustomerService customerService;
    private final ReservationQueryService reservationQueryService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public DashboardService(RevenueService revenueService,
                            AnalyticsRepository repository,
                            BusinessService businessService,
                            CustomerService customerService,
                            ReservationQueryService reservationQueryService,
                            CurrentTenant currentTenant,
                            Clock clock) {
        this.revenueService = revenueService;
        this.repository = repository;
        this.businessService = businessService;
        this.customerService = customerService;
        this.reservationQueryService = reservationQueryService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        currentTenant.requireOwner();
        Business business = businessService.requireById(currentTenant.businessId());
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));

        ReportingPeriod todayPeriod = new ReportingPeriod(today, today, zone);
        ReportingPeriod month = new ReportingPeriod(today.withDayOfMonth(1),
                today.with(TemporalAdjusters.lastDayOfMonth()), zone);
        RevenueSummary todayRevenue = revenueService.summary(business, todayPeriod);
        RevenueSummary monthRevenue = revenueService.summary(business, month);

        return new Dashboard(
                business.getCurrency(),
                todayRevenue.netRevenue(),
                repository.countReservations(business.getId(), todayPeriod.start(), todayPeriod.end()),
                monthRevenue.netRevenue(),
                repository.countReservations(business.getId(), month.start(), month.end()),
                customerService.countFor(business.getId()),
                reservationQueryService.upcoming(8));
    }
}
