package com.bookingsaas.analytics;

import com.bookingsaas.analytics.dto.AnalyticsDtos.CustomerReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.Dashboard;
import com.bookingsaas.analytics.dto.AnalyticsDtos.Granularity;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ReservationReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ResourceUtilization;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenuePeriods;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenueReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.ServiceStat;
import com.bookingsaas.analytics.dto.AnalyticsDtos.SourceStat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard, revenue and analytics. Owner-only. Date ranges are inclusive
 * business-local days; without one, the last 30 days are used.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin · Analytics")
public class AnalyticsController {

    private final DashboardService dashboardService;
    private final RevenueService revenueService;
    private final AnalyticsService analyticsService;

    public AnalyticsController(DashboardService dashboardService,
                               RevenueService revenueService,
                               AnalyticsService analyticsService) {
        this.dashboardService = dashboardService;
        this.revenueService = revenueService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/analytics/revenue/periods")
    @Operation(summary = "Revenue today, this week and this month")
    public RevenuePeriods revenuePeriods() {
        return revenueService.periods();
    }

    @GetMapping("/analytics/revenue")
    @Operation(summary = "Revenue for a date range: gross, discounts, net, count, average, and a time series")
    public RevenueReport revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Granularity granularity) {
        return revenueService.report(from, to, granularity);
    }

    @GetMapping("/analytics/reservations")
    public ReservationReport reservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.reservations(from, to);
    }

    @GetMapping("/analytics/services")
    public List<ServiceStat> services(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.services(from, to);
    }

    @GetMapping("/analytics/sources")
    @Operation(summary = "Reservations and revenue per channel: website, admin, Instagram, Facebook, API")
    public List<SourceStat> sources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.sources(from, to);
    }

    @GetMapping("/analytics/resources")
    public List<ResourceUtilization> resources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.resources(from, to);
    }

    @GetMapping("/analytics/customers")
    public CustomerReport customers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.customers(from, to);
    }
}
