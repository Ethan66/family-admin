package com.ethan.familyadmin.audit;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AuditController {
    private final AuditQueryService service;
    public AuditController(AuditQueryService service) { this.service = service; }
    @GetMapping public ApiResponse<PageResponse<OperationLogResponse>> list(@RequestParam(required=false) Long familyId, @RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") long pageSize) { return ApiResponse.success(service.list(familyId,page,pageSize)); }
}
