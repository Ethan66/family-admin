package com.ethan.familyadmin.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BootstrapAdminRunner implements CommandLineRunner {
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String displayName;
    private final UserMapper users;
    private final PasswordEncoder encoder;

    public BootstrapAdminRunner(
        @Value("${family-admin.bootstrap.enabled:true}") boolean enabled,
        @Value("${family-admin.bootstrap.username:admin}") String username,
        @Value("${family-admin.bootstrap.password:family-admin-123}") String password,
        @Value("${family-admin.bootstrap.display-name:超级管理员}") String displayName,
        UserMapper users,
        PasswordEncoder encoder
    ) {
        this.enabled = enabled; this.username = username; this.password = password; this.displayName = displayName;
        this.users = users; this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled || users.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getSuperAdmin, true)) > 0) return;
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setUsername(username); user.setPasswordHash(encoder.encode(password)); user.setDisplayName(displayName);
        user.setStatus("ACTIVE"); user.setSuperAdmin(true); user.setTokenVersion(0); user.setDeleted(0);
        user.setCreatedAt(now); user.setUpdatedAt(now);
        users.insert(user);
    }
}
