package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/opening-hours")
@Tag(name = "Admin · Business")
public class OpeningHoursController {

    private final OpeningHoursService openingHoursService;

    public OpeningHoursController(OpeningHoursService openingHoursService) {
        this.openingHoursService = openingHoursService;
    }

    @GetMapping
    public List<DayInterval> list() {
        return openingHoursService.current();
    }

    /** Replaces the whole week in one call — partial edits cannot leave gaps. */
    @PutMapping
    public List<DayInterval> replace(@RequestBody List<DayInterval> intervals) {
        return openingHoursService.replace(intervals);
    }
}
