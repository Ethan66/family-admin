package com.ethan.familyadmin.config;

import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapAdminRunnerTest {
    @Test
    void createsFirstSuperAdminWhenEnabled() throws Exception {
        UserMapper users = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.selectCount(any())).thenReturn(0L);
        when(encoder.encode("secret123")).thenReturn("encoded");
        var runner = new BootstrapAdminRunner(true, "admin", "secret123", "系统管理员", users, encoder);

        runner.run();

        var captor = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(users).insert(captor.capture());
        assertThat(captor.getValue().getSuperAdmin()).isTrue();
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
    }
}
