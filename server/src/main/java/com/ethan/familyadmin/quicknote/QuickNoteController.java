package com.ethan.familyadmin.quicknote;

import com.ethan.familyadmin.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/quick-notes")
public class QuickNoteController {
    private final QuickNoteService service;
    public QuickNoteController(QuickNoteService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<QuickNoteResponse>> list() { return ApiResponse.success(service.list()); }

    @PostMapping
    public ApiResponse<QuickNoteResponse> create(@Valid @RequestBody QuickNotePayload payload) {
        return ApiResponse.success(service.create(payload));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuickNoteResponse> update(@PathVariable Long id, @Valid @RequestBody QuickNotePayload payload) {
        return ApiResponse.success(service.update(id, payload));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam @Min(0) int version) {
        service.delete(id, version); return ApiResponse.success(null);
    }
}
