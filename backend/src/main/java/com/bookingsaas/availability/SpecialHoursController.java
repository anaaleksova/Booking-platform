package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.SpecialDayRequest;
import com.bookingsaas.availability.dto.SpecialDayResponse;
import com.bookingsaas.availability.spi.ScheduleConflict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Dates whose opening hours differ from the week: holidays, extra open days, short days. */
@RestController
@RequestMapping("/api/admin/special-hours")
@Tag(name = "Admin · Business")
public class SpecialHoursController {

    private final SpecialHoursService specialHoursService;

    public SpecialHoursController(SpecialHoursService specialHoursService) {
        this.specialHoursService = specialHoursService;
    }

    @GetMapping
    @Operation(summary = "Special dates from today on, with the bookings each leaves outside its hours")
    public List<SpecialDayResponse> list() {
        return specialHoursService.upcoming();
    }

    @PostMapping("/{date}/conflicts")
    @Operation(summary = "The bookings these hours would leave outside, without saving")
    public List<ScheduleConflict> preview(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @Valid @RequestBody SpecialDayRequest request) {
        return specialHoursService.preview(date, request);
    }

    @PutMapping("/{date}")
    @Operation(summary = "Set one date's hours; no hours closes the business that day")
    public SpecialDayResponse save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @Valid @RequestBody SpecialDayRequest request) {
        return specialHoursService.save(date, request);
    }

    @DeleteMapping("/{date}")
    @Operation(summary = "Back to the weekly hours on that date")
    public ResponseEntity<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        specialHoursService.delete(date);
        return ResponseEntity.noContent().build();
    }
}
