package com.bookingsaas.analytics;

import com.bookingsaas.analytics.AnalyticsRepository.RevenuePeriodRow;
import com.bookingsaas.analytics.AnalyticsRepository.RevenueRow;
import com.bookingsaas.analytics.dto.AnalyticsDtos.Granularity;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenuePeriods;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenuePoint;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenueReport;
import com.bookingsaas.analytics.dto.AnalyticsDtos.RevenueSummary;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Revenue figures. What counts is defined once, in {@link AnalyticsRepository}:
 * the final price of CONFIRMED and COMPLETED reservations, attributed to the
 * day the reservation starts.
 */
@Service
public class RevenueService {

    private final AnalyticsRepository repository;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public RevenueService(AnalyticsRepository repository, BusinessService businessService,
                          CurrentTenant currentTenant, Clock clock) {
        this.repository = repository;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    /** Today, this (ISO) week and this month — each covering the whole period, not just up to now. */
    @Transactional(readOnly = true)
    public RevenuePeriods periods() {
        currentTenant.requireOwner();
        Business business = businessService.requireById(currentTenant.businessId());
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        return new RevenuePeriods(
                summary(business, new ReportingPeriod(today, today, zone)),
                summary(business, new ReportingPeriod(weekStart, weekStart.plusDays(6), zone)),
                summary(business, new ReportingPeriod(monthStart, today.with(TemporalAdjusters.lastDayOfMonth()), zone)));
    }

    /** A custom range with a gap-free series, so charts show quiet days as zero rather than skipping them. */
    @Transactional(readOnly = true)
    public RevenueReport report(LocalDate from, LocalDate to, Granularity granularity) {
        currentTenant.requireOwner();
        Business business = businessService.requireById(currentTenant.businessId());
        ZoneId zone = business.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        ReportingPeriod period = ReportingPeriod.of(from, to, today.minusDays(29), today, zone);
        Granularity unit = granularity != null ? granularity : Granularity.DAY;

        Map<LocalDate, RevenuePeriodRow> rows = repository.revenueByPeriod(business.getId(), period.start(),
                        period.end(), zone.getId(), unit.name().toLowerCase())
                .stream().collect(Collectors.toMap(RevenuePeriodRow::period, Function.identity()));

        List<RevenuePoint> series = new ArrayList<>();
        for (LocalDate bucket = bucketStart(period.from(), unit); !bucket.isAfter(period.to());
             bucket = next(bucket, unit)) {
            RevenuePeriodRow row = rows.get(bucket);
            series.add(row == null
                    ? new RevenuePoint(bucket, 0, zero(), zero(), zero())
                    : new RevenuePoint(bucket, row.reservations(), row.gross(), row.discounts(), row.net()));
        }
        return new RevenueReport(summary(business, period), unit, series);
    }

    RevenueSummary summary(Business business, ReportingPeriod period) {
        RevenueRow row = repository.revenue(business.getId(), period.start(), period.end());
        BigDecimal average = row.reservations() == 0 ? zero()
                : row.net().divide(BigDecimal.valueOf(row.reservations()), 2, RoundingMode.HALF_UP);
        return new RevenueSummary(period.from(), period.to(), row.reservations(), row.gross(), row.discounts(),
                row.net(), average, business.getCurrency());
    }

    private static LocalDate bucketStart(LocalDate date, Granularity unit) {
        return switch (unit) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate bucket, Granularity unit) {
        return switch (unit) {
            case DAY -> bucket.plusDays(1);
            case WEEK -> bucket.plusWeeks(1);
            case MONTH -> bucket.plusMonths(1);
        };
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
