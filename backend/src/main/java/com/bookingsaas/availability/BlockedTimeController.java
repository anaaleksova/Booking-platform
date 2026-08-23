package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.BlockedTimeRequest;
import com.bookingsaas.availability.dto.BlockedTimeResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/blocked-times")
public class BlockedTimeController {

    private final BlockedTimeService blockedTimeService;

    public BlockedTimeController(BlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @GetMapping
    public List<BlockedTimeResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return blockedTimeService.list(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedTimeResponse create(@Valid @RequestBody BlockedTimeRequest request) {
        return blockedTimeService.create(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        blockedTimeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
