package com.ethan.familyadmin.family;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.family.admin.AdminFamilyService;
import com.ethan.familyadmin.family.dto.MemberResponse;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FamilyMemberOptionsServiceTest {
    private final AdminFamilyService familyService = mock(AdminFamilyService.class);
    private final FamilyMemberOptionsService service = new FamilyMemberOptionsService(familyService, new TenantAccessGuard());

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listsOnlyActiveMembersOfAuthenticatedFamily() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        when(familyService.listMembers(31L)).thenReturn(List.of(
            new MemberResponse(7L, 31L, "dad", "爸爸", "ACTIVE", Role.FAMILY_MEMBER),
            new MemberResponse(8L, 31L, "mom", "妈妈", "DISABLED", Role.FAMILY_MEMBER)));

        var options = service.list();

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().userId()).isEqualTo(7L);
        assertThat(options.getFirst().displayName()).isEqualTo("爸爸");
        verify(familyService).listMembers(31L);
    }

    @Test
    void usesAdministratorsSelectedFamily() {
        TenantContext.set(new CurrentUser(1L, 42L, Role.SUPER_ADMIN, 0));
        when(familyService.listMembers(42L)).thenReturn(List.of());
        assertThat(service.list()).isEmpty();
        verify(familyService).listMembers(42L);
    }

    @Test
    void rejectsMissingFamilyBeforeReadingMembers() {
        TenantContext.set(new CurrentUser(1L, null, Role.SUPER_ADMIN, 0));
        assertThatThrownBy(service::list).isInstanceOf(BusinessException.class);
        verifyNoInteractions(familyService);
    }
}
