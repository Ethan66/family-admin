package com.ethan.familyadmin.movie;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.movie.dto.CreateMovieRequest;
import com.ethan.familyadmin.movie.dto.MovieResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/movies")
public class MovieController {
    private final MovieService service;
    public MovieController(MovieService service) { this.service = service; }
    @GetMapping public ApiResponse<PageResponse<MovieResponse>> list(@RequestParam(required=false) String type, @RequestParam(required=false) WatchStatus status, @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") long pageSize) { return ApiResponse.success(service.list(type, status, keyword, page, pageSize)); }
    @PostMapping public ApiResponse<MovieResponse> create(@Valid @RequestBody CreateMovieRequest request) { return ApiResponse.success(service.create(request)); }
    @PutMapping("/{id}") public ApiResponse<MovieResponse> update(@PathVariable Long id, @Valid @RequestBody CreateMovieRequest request) { return ApiResponse.success(service.update(id, request)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
}
