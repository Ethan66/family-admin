package com.ethan.familyadmin.note;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notes")
public class StudyNoteController {
    private final StudyNoteService service;
    public StudyNoteController(StudyNoteService service) { this.service = service; }
    @GetMapping public ApiResponse<PageResponse<StudyNoteResponse>> list(@RequestParam(required=false) String tag, @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") long pageSize) { return ApiResponse.success(service.list(tag, keyword, page, pageSize)); }
    @GetMapping("/{id}") public ApiResponse<StudyNoteResponse> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
}
