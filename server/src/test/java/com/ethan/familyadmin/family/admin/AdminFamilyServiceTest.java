package com.ethan.familyadmin.family.admin;

import com.ethan.familyadmin.audit.AuditService;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.family.dto.CreateMemberRequest;
import com.ethan.familyadmin.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class AdminFamilyServiceTest {

    @Mock
    private FamilyMapper familyMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FamilyMemberMapper familyMemberMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private AdminFamilyService service;

    @BeforeEach
    void setUp() {
        service = new AdminFamilyService(familyMapper, userMapper, familyMemberMapper, passwordEncoder, auditService);
    }

    @Test
    void createMemberAssignsExactlyOneRequestedFamily() {
        FamilyEntity family = new FamilyEntity();
        family.setId(31L);
        family.setStatus("ACTIVE");
        when(familyMapper.selectById(31L)).thenReturn(family);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            invocation.<UserEntity>getArgument(0).setId(8L);
            return 1;
        });

        service.createMember(31L, new CreateMemberRequest("father", "父亲", "secret123"));

        ArgumentCaptor<FamilyMemberEntity> captor = ArgumentCaptor.forClass(FamilyMemberEntity.class);
        verify(familyMemberMapper).insert(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(31L);
        assertThat(captor.getValue().getUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getRole()).isEqualTo(Role.FAMILY_MEMBER.name());
        verify(auditService).record("MEMBER_CREATE", "USER", "8", 31L);
    }

    @Test
    void duplicateUsernameIsRejectedBeforeInsert() {
        FamilyEntity family = new FamilyEntity();
        family.setId(31L);
        family.setStatus("ACTIVE");
        when(familyMapper.selectById(31L)).thenReturn(family);
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createMember(31L, new CreateMemberRequest(
            "father", "父亲", "secret123"
        ))).isInstanceOf(BusinessException.class).hasMessage("登录名已存在");

        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(familyMemberMapper, never()).insert(any(FamilyMemberEntity.class));
    }
}
