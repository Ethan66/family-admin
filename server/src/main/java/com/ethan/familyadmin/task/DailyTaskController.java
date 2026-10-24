package com.ethan.familyadmin.task;

import com.ethan.familyadmin.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import com.ethan.familyadmin.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/daily-tasks")
public class DailyTaskController {
    private final DailyTaskService service;
    public DailyTaskController(DailyTaskService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<TaskOccurrence>> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long memberId) { return ApiResponse.success(service.list(from, to, memberId)); }
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody TaskPayload request) { service.create(request); return ApiResponse.success(null); }
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TaskPayload request) { service.update(id, request); return ApiResponse.success(null); }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
    public record CompletionRequest(@NotNull LocalDate date, @NotNull Boolean completed) {}
    @PutMapping("/{id}/completion")
    public ApiResponse<Void> complete(@PathVariable Long id, @Valid @RequestBody CompletionRequest request) {
        service.complete(id, request.date(), request.completed()); return ApiResponse.success(null);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidInput() {
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.getCode(), "任务日期、时间或参数格式不正确"));
    }
}
