package com.ethan.familyadmin.auth;

import com.ethan.familyadmin.auth.dto.LoginRequest;
import com.ethan.familyadmin.auth.dto.LoginResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.security.JwtService;
import com.ethan.familyadmin.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private FamilyMapper familyMapper;
    @Mock
    private FamilyMemberMapper familyMemberMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, familyMapper, familyMemberMapper, passwordEncoder, jwtService);
    }

    @Test
    void disabledUserCannotLogin() {
        UserEntity user = user(5L, "DISABLED");
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled", "secret123")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("账号或密码错误");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void familyUserLoginUsesMembershipTenant() {
        UserEntity user = user(5L, "ACTIVE");
        FamilyMemberEntity membership = new FamilyMemberEntity();
        membership.setFamilyId(31L);
        membership.setRole(Role.FAMILY_MEMBER.name());
        FamilyEntity family = new FamilyEntity();
        family.setId(31L);
        family.setStatus("ACTIVE");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", user.getPasswordHash())).thenReturn(true);
        when(familyMemberMapper.selectOne(any())).thenReturn(membership);
        when(familyMapper.selectById(31L)).thenReturn(family);
        when(jwtService.issue(user, membership)).thenReturn("signed-token");

        LoginResponse response = authService.login(new LoginRequest("member", "secret123"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.user().familyId()).isEqualTo(31L);
        assertThat(response.user().role()).isEqualTo(Role.FAMILY_MEMBER);
    }

    private UserEntity user(Long id, String status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("member");
        user.setDisplayName("家庭成员");
        user.setPasswordHash("encoded-password");
        user.setStatus(status);
        user.setSuperAdmin(false);
        user.setTokenVersion(2);
        return user;
    }
}
