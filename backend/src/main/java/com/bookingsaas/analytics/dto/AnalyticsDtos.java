package com.bookingsaas.analytics.dto;

import com.bookingsaas.reservation.dto.ReservationResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response shapes of the analytics API, grouped in one file because each is a
 * small read-only projection with no behaviour.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /**
     * Revenue over an inclusive date range. Counts CONFIRMED and COMPLETED
     * reservations by start date; cancelled and pending ones are excluded.
     *
     * @param grossRevenue            before discounts
     * @param netRevenue              after discounts — "revenue"
     * @param averageReservationValue net revenue per counted reservation
     */
    public record RevenueSummary(
            LocalDate from,
            LocalDate to,
            long reservations,
            BigDecimal grossRevenue,
            BigDecimal discounts,
            BigDecimal netRevenue,
            BigDecimal averageReservationValue,
            String currency
    ) {
    }

    public record RevenuePeriods(RevenueSummary today, RevenueSummary thisWeek, RevenueSummary thisMonth) {
    }

    public enum Granularity { DAY, WEEK, MONTH }

    /** @param periodStart first day of the day, ISO week or month */
    public record RevenuePoint(LocalDate periodStart, long reservations, BigDecimal grossRevenue,
                               BigDecimal discounts, BigDecimal netRevenue) {
    }

    public record RevenueReport(RevenueSummary summary, Granularity granularity, List<RevenuePoint> series) {
    }

    public record ReservationPoint(LocalDate date, long total, long pending, long confirmed, long completed,
                                   long cancelled, long participants) {
    }

    public record ReservationReport(LocalDate from, LocalDate to, long total, long cancelled,
                                    List<ReservationPoint> series) {
    }

    /** @param reservations non-cancelled reservations; revenue follows the revenue definition */
    public record ServiceStat(Long serviceId, String serviceName, boolean active, long reservations,
                              long participants, BigDecimal revenue) {
    }

    /**
     * @param availableMinutes minutes the resource could be booked in the range:
     *                         its own hours (or the opening hours) per day
     * @param utilization      booked / available, 0..1
     */
    public record ResourceUtilization(Long resourceId, String resourceName, Long resourceTypeId,
                                      String resourceTypeName, long reservations, long bookedMinutes,
                                      long availableMinutes, double utilization) {
    }

    /**
     * One reservation channel. Counts exclude cancellations; revenue follows
     * the revenue definition.
     *
     * @param share        of all non-cancelled reservations in the range, 0..1
     * @param revenueShare of all revenue in the range, 0..1
     */
    public record SourceStat(String source, long reservations, double share, BigDecimal revenue,
                             double revenueShare) {
    }

    public record TopCustomer(Long customerId, String name, long reservations, BigDecimal spent) {
    }

    /**
     * @param newCustomers       first-ever reservation falls in the range
     * @param returningCustomers had reserved before the range, and again in it
     */
    public record CustomerReport(LocalDate from, LocalDate to, long activeCustomers, long newCustomers,
                                 long returningCustomers, List<TopCustomer> topCustomers) {
    }

    /** The admin landing page's KPI cards plus the next reservations. */
    public record Dashboard(
            String currency,
            BigDecimal todayRevenue,
            long todayReservations,
            BigDecimal monthRevenue,
            long monthReservations,
            long totalCustomers,
            List<ReservationResponse> upcoming
    ) {
    }
}
