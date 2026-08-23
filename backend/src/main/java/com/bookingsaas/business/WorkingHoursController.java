package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/working-hours")
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    public WorkingHoursController(WorkingHoursService workingHoursService) {
        this.workingHoursService = workingHoursService;
    }

    @GetMapping
    public List<DayInterval> list() {
        return workingHoursService.current();
    }

    @PutMapping
    public List<DayInterval> replace(@RequestBody List<DayInterval> intervals) {
        return workingHoursService.replace(intervals);
    }
}
