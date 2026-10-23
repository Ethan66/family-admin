package com.ethan.familyadmin.family;

import com.ethan.familyadmin.common.api.ApiResponse;
import com.ethan.familyadmin.family.dto.MemberOptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/family/members")
public class FamilyMemberController {
    private final FamilyMemberOptionsService memberOptionsService;

    public FamilyMemberController(FamilyMemberOptionsService memberOptionsService) {
        this.memberOptionsService = memberOptionsService;
    }

    @GetMapping
    public ApiResponse<List<MemberOptionResponse>> list() {
        return ApiResponse.success(memberOptionsService.list());
    }
}
