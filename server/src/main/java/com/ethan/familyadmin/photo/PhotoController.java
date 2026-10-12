package com.ethan.familyadmin.photo;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.photo.dto.CreatePhotoRequest;
import com.ethan.familyadmin.photo.dto.PhotoResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    private final PhotoService service;
    public PhotoController(PhotoService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResponse<PhotoResponse>> list(@RequestParam(required = false) String album,
        @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.list(album, keyword, page, pageSize));
    }
    @PostMapping public ApiResponse<PhotoResponse> create(@Valid @RequestBody CreatePhotoRequest request) { return ApiResponse.success(service.create(request)); }
    @PutMapping("/{id}") public ApiResponse<PhotoResponse> update(@PathVariable Long id, @Valid @RequestBody CreatePhotoRequest request) { return ApiResponse.success(service.update(id, request)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
}
