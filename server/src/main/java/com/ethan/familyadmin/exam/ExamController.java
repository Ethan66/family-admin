package com.ethan.familyadmin.exam;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.exam.dto.CreateExamRequest;
import com.ethan.familyadmin.exam.dto.ExamResponse;
import com.ethan.familyadmin.exam.dto.ReviewExamRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExamResponse>> list(
        @RequestParam(required = false) String subject,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) ExamStatus status,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(service.list(subject, grade, status, page, pageSize));
    }

    @PostMapping
    public ApiResponse<ExamResponse> create(@Valid @RequestBody CreateExamRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamResponse> update(@PathVariable Long id, @Valid @RequestBody CreateExamRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PutMapping("/{id}/review")
    public ApiResponse<ExamResponse> review(@PathVariable Long id, @Valid @RequestBody ReviewExamRequest request) {
        return ApiResponse.success(service.review(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
