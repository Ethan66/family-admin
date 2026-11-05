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
import static org.mockito.Mockito.*;

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

        when(jwt.renew(new CurrentUser(1L,8L,Role.SUPER_ADMIN,0))).thenReturn("renewed-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(TenantContext.requiredFamilyId()).isEqualTo(8L);
            assertThat(response.getHeader("X-Access-Token")).isEqualTo("renewed-token");
            res.getWriter().write("ok");
            res.flushBuffer();
        });
        assertThat(response.getHeader("X-Access-Token")).isEqualTo("renewed-token");
    }

    @Test void expiredTokenCannotRenew() throws Exception {
        var jwt = mock(JwtService.class);
        when(jwt.verify("expired")).thenThrow(new IllegalArgumentException("expired"));
        var filter = new JwtAuthenticationFilter(jwt,mock(UserMapper.class),mock(FamilyMapper.class),mock(FamilyMemberMapper.class));
        var request = new MockHttpServletRequest(); request.addHeader("Authorization","Bearer expired");
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res) -> {});
        assertThat(response.getHeader("X-Access-Token")).isNull();
        verify(jwt,never()).renew(any());
    }

    @Test void changedPasswordOrDisabledAccountCannotRenew() throws Exception {
        for (boolean disabled : new boolean[]{false,true}) {
            var jwt = mock(JwtService.class); var users = mock(UserMapper.class);
            when(jwt.verify("token")).thenReturn(new JwtService.VerifiedToken(1L,0));
            var user = new UserEntity(); user.setId(1L); user.setStatus(disabled ? "DISABLED" : "ACTIVE");
            user.setTokenVersion(disabled ? 0 : 1); when(users.selectById(1L)).thenReturn(user);
            var filter = new JwtAuthenticationFilter(jwt,users,mock(FamilyMapper.class),mock(FamilyMemberMapper.class));
            var request = new MockHttpServletRequest(); request.addHeader("Authorization","Bearer token");
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res) -> org.assertj.core.api.Assertions.assertThatThrownBy(TenantContext::requiredUser).isInstanceOf(IllegalStateException.class));
            verify(jwt,never()).renew(any());
            assertThat(response.getHeader("X-Access-Token")).isNull();
        }
    }
}
