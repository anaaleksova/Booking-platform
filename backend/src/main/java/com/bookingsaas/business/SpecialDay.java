package com.bookingsaas.business;

import com.bookingsaas.common.domain.Auditable;
import com.bookingsaas.common.domain.DayInterval;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Opening hours for one date, used instead of the weekly hours on that date:
 * shorter hours on Dec 24, an extra open Sunday, a day closed for an event.
 *
 * <p>No hours means closed all day.
 */
@Entity
@Table(name = "special_day")
@Getter
@Setter
@NoArgsConstructor
public class SpecialDay extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(length = 255)
    private String note;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "special_day_hours", joinColumns = @JoinColumn(name = "special_day_id"))
    @OrderBy("startTime")
    private List<Hours> hours = new ArrayList<>();

    public boolean isClosed() {
        return hours.isEmpty();
    }

    /** The hours as weekday intervals of this date, the shape the availability engine reads. */
    public List<DayInterval> intervals() {
        int dayOfWeek = day.getDayOfWeek().getValue();
        return hours.stream().map(h -> new DayInterval(dayOfWeek, h.getStartTime(), h.getEndTime())).toList();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    public static class Hours {

        @Column(name = "start_time", nullable = false)
        private LocalTime startTime;

        @Column(name = "end_time", nullable = false)
        private LocalTime endTime;

        public Hours(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
