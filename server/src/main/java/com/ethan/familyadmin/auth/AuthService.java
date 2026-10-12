package com.ethan.familyadmin.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.auth.dto.ChangePasswordRequest;
import com.ethan.familyadmin.auth.dto.CurrentUserResponse;
import com.ethan.familyadmin.auth.dto.LoginRequest;
import com.ethan.familyadmin.auth.dto.LoginResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.JwtService;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOGIN_FAILURE = "账号或密码错误";

    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
        UserMapper userMapper,
        FamilyMapper familyMapper,
        FamilyMemberMapper familyMemberMapper,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userMapper = userMapper;
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
            .eq(UserEntity::getUsername, request.username())
            .eq(UserEntity::getDeleted, 0));
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            throw loginFailure();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw loginFailure();
        }

        FamilyMemberEntity membership = null;
        Role role = Role.SUPER_ADMIN;
        Long familyId = null;
        if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
            membership = loadMembership(user.getId());
            FamilyEntity family = familyMapper.selectById(membership.getFamilyId());
            if (family == null || !ACTIVE.equals(family.getStatus())) {
                throw loginFailure();
            }
            familyId = membership.getFamilyId();
            role = Role.valueOf(membership.getRole());
        }

        CurrentUserResponse currentUser = toResponse(user, familyId, role);
        return new LoginResponse(jwtService.issue(user, membership), currentUser);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        CurrentUser current = TenantContext.requiredUser();
        UserEntity user = requireActiveUser(current.userId());
        return toResponse(user, current.familyId(), current.role());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        CurrentUser current = TenantContext.requiredUser();
        UserEntity user = requireActiveUser(current.userId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userMapper.updateById(user);
    }

    private FamilyMemberEntity loadMembership(Long userId) {
        FamilyMemberEntity membership = familyMemberMapper.selectOne(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getUserId, userId)
            .eq(FamilyMemberEntity::getDeleted, 0));
        if (membership == null) {
            throw loginFailure();
        }
        return membership;
    }

    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private CurrentUserResponse toResponse(UserEntity user, Long familyId, Role role) {
        return new CurrentUserResponse(user.getId(), familyId, user.getUsername(), user.getDisplayName(), role);
    }

    private BusinessException loginFailure() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE);
    }
}

