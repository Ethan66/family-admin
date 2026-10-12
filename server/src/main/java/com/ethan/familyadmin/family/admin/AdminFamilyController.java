package com.ethan.familyadmin.family.admin;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.family.dto.ChangeStatusRequest;
import com.ethan.familyadmin.family.dto.CreateFamilyRequest;
import com.ethan.familyadmin.family.dto.CreateMemberRequest;
import com.ethan.familyadmin.family.dto.FamilyResponse;
import com.ethan.familyadmin.family.dto.MemberResponse;
import com.ethan.familyadmin.family.dto.UpdateMemberRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/families")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminFamilyController {

    private final AdminFamilyService adminFamilyService;

    public AdminFamilyController(AdminFamilyService adminFamilyService) {
        this.adminFamilyService = adminFamilyService;
    }

    @GetMapping
    public ApiResponse<List<FamilyResponse>> listFamilies() {
        return ApiResponse.success(adminFamilyService.listFamilies());
    }

    @PostMapping
    public ApiResponse<FamilyResponse> createFamily(@Valid @RequestBody CreateFamilyRequest request) {
        return ApiResponse.success(adminFamilyService.createFamily(request));
    }

    @PutMapping("/{familyId}/status")
    public ApiResponse<Void> changeFamilyStatus(
        @PathVariable Long familyId,
        @Valid @RequestBody ChangeStatusRequest request
    ) {
        adminFamilyService.changeFamilyStatus(familyId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{familyId}/members")
    public ApiResponse<List<MemberResponse>> listMembers(@PathVariable Long familyId) {
        return ApiResponse.success(adminFamilyService.listMembers(familyId));
    }

    @PostMapping("/{familyId}/members")
    public ApiResponse<MemberResponse> createMember(
        @PathVariable Long familyId,
        @Valid @RequestBody CreateMemberRequest request
    ) {
        return ApiResponse.success(adminFamilyService.createMember(familyId, request));
    }

    @PutMapping("/{familyId}/members/{userId}")
    public ApiResponse<MemberResponse> updateMember(
        @PathVariable Long familyId,
        @PathVariable Long userId,
        @Valid @RequestBody UpdateMemberRequest request
    ) {
        return ApiResponse.success(adminFamilyService.updateMember(familyId, userId, request));
    }
}

