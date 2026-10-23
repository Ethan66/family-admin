package com.ethan.familyadmin.family;

import com.ethan.familyadmin.family.admin.AdminFamilyService;
import com.ethan.familyadmin.family.dto.MemberOptionResponse;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FamilyMemberOptionsService {
    private final AdminFamilyService familyService;
    private final TenantAccessGuard accessGuard;

    public FamilyMemberOptionsService(AdminFamilyService familyService, TenantAccessGuard accessGuard) {
        this.familyService = familyService;
        this.accessGuard = accessGuard;
    }

    /** 仅从认证后的家庭上下文读取可选成员，不接受客户端指定其他家庭。 */
    @Transactional(readOnly = true)
    public List<MemberOptionResponse> list() {
        accessGuard.requireBusinessCreate(TenantContext.requiredUser());
        return familyService.listMembers(TenantContext.requiredFamilyId()).stream()
            .filter(member -> "ACTIVE".equals(member.status()))
            .map(member -> new MemberOptionResponse(member.userId(), member.displayName()))
            .toList();
    }
}
