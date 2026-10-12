package com.ethan.familyadmin.family.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.audit.AuditService;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.family.dto.ChangeStatusRequest;
import com.ethan.familyadmin.family.dto.CreateFamilyRequest;
import com.ethan.familyadmin.family.dto.CreateMemberRequest;
import com.ethan.familyadmin.family.dto.FamilyResponse;
import com.ethan.familyadmin.family.dto.MemberResponse;
import com.ethan.familyadmin.family.dto.UpdateMemberRequest;
import com.ethan.familyadmin.security.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminFamilyService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    private final FamilyMapper familyMapper;
    private final UserMapper userMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminFamilyService(
        FamilyMapper familyMapper,
        UserMapper userMapper,
        FamilyMemberMapper familyMemberMapper,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.familyMapper = familyMapper;
        this.userMapper = userMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> listFamilies() {
        return familyMapper.selectList(Wrappers.<FamilyEntity>lambdaQuery()
                .eq(FamilyEntity::getDeleted, 0)
                .orderByDesc(FamilyEntity::getId)
                .last("LIMIT 200"))
            .stream().map(this::toFamilyResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public FamilyResponse createFamily(CreateFamilyRequest request) {
        FamilyEntity family = new FamilyEntity();
        family.setName(request.name().trim());
        family.setRemark(request.remark());
        family.setStatus(ACTIVE);
        familyMapper.insert(family);
        auditService.record("FAMILY_CREATE", "FAMILY", String.valueOf(family.getId()), family.getId());
        return toFamilyResponse(family);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeFamilyStatus(Long familyId, ChangeStatusRequest request) {
        FamilyEntity family = requireFamily(familyId, false);
        family.setStatus(request.active() ? ACTIVE : DISABLED);
        familyMapper.updateById(family);
        auditService.record("FAMILY_STATUS_CHANGE", "FAMILY", String.valueOf(familyId), familyId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long familyId) {
        requireFamily(familyId, false);
        List<FamilyMemberEntity> memberships = familyMemberMapper.selectList(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, familyId)
            .eq(FamilyMemberEntity::getDeleted, 0)
            .last("LIMIT 200"));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = memberships.stream().map(FamilyMemberEntity::getUserId).toList();
        var users = userMapper.selectBatchIds(userIds).stream()
            .collect(java.util.stream.Collectors.toMap(UserEntity::getId, user -> user));
        return memberships.stream()
            .filter(membership -> users.containsKey(membership.getUserId()))
            .map(membership -> toMemberResponse(users.get(membership.getUserId()), membership))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public MemberResponse createMember(Long familyId, CreateMemberRequest request) {
        requireFamily(familyId, true);
        long usernameCount = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
            .eq(UserEntity::getUsername, request.username())
            .eq(UserEntity::getDeleted, 0));
        if (usernameCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(ACTIVE);
        user.setSuperAdmin(false);
        user.setTokenVersion(0);
        userMapper.insert(user);

        FamilyMemberEntity membership = new FamilyMemberEntity();
        membership.setFamilyId(familyId);
        membership.setUserId(user.getId());
        membership.setRole(Role.FAMILY_MEMBER.name());
        familyMemberMapper.insert(membership);
        auditService.record("MEMBER_CREATE", "USER", String.valueOf(user.getId()), familyId);
        return toMemberResponse(user, membership);
    }

    @Transactional(rollbackFor = Exception.class)
    public MemberResponse updateMember(Long familyId, Long userId, UpdateMemberRequest request) {
        FamilyMemberEntity membership = requireMembership(familyId, userId);
        UserEntity user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成员不存在");
        }
        user.setDisplayName(request.displayName());
        user.setStatus(request.active() ? ACTIVE : DISABLED);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userMapper.updateById(user);
        membership.setRole(Role.FAMILY_MEMBER.name());
        familyMemberMapper.updateById(membership);
        auditService.record("MEMBER_UPDATE", "USER", String.valueOf(userId), familyId);
        return toMemberResponse(user, membership);
    }

    private FamilyEntity requireFamily(Long familyId, boolean requireActive) {
        FamilyEntity family = familyMapper.selectById(familyId);
        if (family == null || (requireActive && !ACTIVE.equals(family.getStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭不存在或已停用");
        }
        return family;
    }

    private FamilyMemberEntity requireMembership(Long familyId, Long userId) {
        FamilyMemberEntity membership = familyMemberMapper.selectOne(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, familyId)
            .eq(FamilyMemberEntity::getUserId, userId)
            .eq(FamilyMemberEntity::getDeleted, 0));
        if (membership == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成员不存在");
        }
        return membership;
    }

    private FamilyResponse toFamilyResponse(FamilyEntity family) {
        return new FamilyResponse(family.getId(), family.getName(), family.getStatus(), family.getRemark());
    }

    private MemberResponse toMemberResponse(UserEntity user, FamilyMemberEntity membership) {
        return new MemberResponse(
            user.getId(),
            membership.getFamilyId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getStatus(),
            Role.valueOf(membership.getRole())
        );
    }
}
