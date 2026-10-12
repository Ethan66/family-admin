package com.ethan.familyadmin.security;

import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    @Test
    void superAdminMaySelectActiveFamilyWithHeader() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserMapper users = mock(UserMapper.class);
        FamilyMapper families = mock(FamilyMapper.class);
        when(jwt.verify("token")).thenReturn(new JwtService.VerifiedToken(1L, 0));
        UserEntity user = new UserEntity(); user.setId(1L); user.setStatus("ACTIVE"); user.setSuperAdmin(true); user.setTokenVersion(0);
        when(users.selectById(1L)).thenReturn(user);
        FamilyEntity family = new FamilyEntity(); family.setId(8L); family.setStatus("ACTIVE");
        when(families.selectById(8L)).thenReturn(family);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users, families, mock(FamilyMemberMapper.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token"); request.addHeader("X-Family-Id", "8");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
            assertThat(TenantContext.requiredFamilyId()).isEqualTo(8L));
    }
}
