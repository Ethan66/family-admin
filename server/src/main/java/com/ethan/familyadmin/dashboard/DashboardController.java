package com.ethan.familyadmin.dashboard;

import com.ethan.familyadmin.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public ApiResponse<DashboardOverview> overview() { return ApiResponse.success(service.overview()); }
}
