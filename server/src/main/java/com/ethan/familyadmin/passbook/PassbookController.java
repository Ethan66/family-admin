package com.ethan.familyadmin.passbook;

import com.ethan.familyadmin.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/passbooks")
public class PassbookController {
    private final PassbookService service;
    public PassbookController(PassbookService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PassbookPageResponse> list(
        @RequestParam(required = false) Long memberId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize,
        @RequestParam(defaultValue = "DEPOSIT_DATE_DESC") PassbookSort sort
    ) { return ApiResponse.success(service.list(memberId, startDate, endDate, page, pageSize, sort)); }

    @PostMapping
    public ApiResponse<PassbookResponse> create(@Valid @RequestBody PassbookPayload payload) {
        return ApiResponse.success(service.create(payload));
    }

    @PutMapping("/{id}")
    public ApiResponse<PassbookResponse> update(@PathVariable Long id, @Valid @RequestBody PassbookPayload payload) {
        return ApiResponse.success(service.update(id, payload));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
