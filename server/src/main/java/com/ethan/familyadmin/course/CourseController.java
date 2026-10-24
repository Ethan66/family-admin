package com.ethan.familyadmin.course;
import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

@RestController @RequestMapping("/api/courses")
public class CourseController {
    private final CourseService service;
    public CourseController(CourseService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<CourseOccurrence>> list(@RequestParam(required = false) Long memberId) {
        return ApiResponse.success(service.list(memberId));
    }
    @GetMapping("/records")
    public ApiResponse<CourseService.RecordPage> records(@RequestParam(required = false) Long memberId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String title,
        @RequestParam(required = false) String status, @RequestParam(required = false) String month) {
        return ApiResponse.success(service.history(memberId, page, title, status, month));
    }
    @GetMapping("/balances")
    public ApiResponse<List<CourseService.LessonBalance>> balances(@RequestParam(required = false) Long memberId) {
        return ApiResponse.success(service.balances(memberId));
    }
    public record BalancePayload(@NotNull @Positive Long memberId, @NotBlank @Size(max = 120) String title,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal remainingLessons,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal totalLessons) {}
    @PutMapping("/balances")
    public ApiResponse<Void> saveBalance(@Valid @RequestBody BalancePayload payload) {
        service.saveBalance(payload.memberId(), payload.title(), payload.remainingLessons(), payload.totalLessons()); return ApiResponse.success(null);
    }
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CoursePayload payload) { service.create(payload); return ApiResponse.success(null); }
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CoursePayload payload) { service.update(id, payload); return ApiResponse.success(null); }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
    @PutMapping("/{id}/attendance")
    public ApiResponse<Void> record(@PathVariable Long id, @Valid @RequestBody AttendancePayload payload) {
        service.record(id, payload); return ApiResponse.success(null);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidInput() {
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.getCode(), "课程日期、时间或参数格式不正确"));
    }
}
