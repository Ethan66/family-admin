package com.ethan.familyadmin.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.family.FamilyEntity;
import com.ethan.familyadmin.family.FamilyMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACTIVE = "ACTIVE";

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        UserMapper userMapper,
        FamilyMapper familyMapper,
        FamilyMemberMapper familyMemberMapper
    ) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            CurrentUser currentUser = authenticate(request);
            if (currentUser != null) {
                TenantContext.set(currentUser);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    currentUser,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private CurrentUser authenticate(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            JwtService.VerifiedToken token = jwtService.verify(authorization.substring(7));
            UserEntity user = userMapper.selectById(token.userId());
            if (user == null || !ACTIVE.equals(user.getStatus()) || user.getTokenVersion() != token.tokenVersion()) {
                return null;
            }
            if (Boolean.TRUE.equals(user.getSuperAdmin())) {
                Long selectedFamilyId = selectedFamilyId(request);
                return new CurrentUser(user.getId(), selectedFamilyId, Role.SUPER_ADMIN, user.getTokenVersion());
            }
            FamilyMemberEntity membership = familyMemberMapper.selectOne(Wrappers.<FamilyMemberEntity>lambdaQuery()
                .eq(FamilyMemberEntity::getUserId, user.getId())
                .eq(FamilyMemberEntity::getDeleted, 0));
            if (membership == null) {
                return null;
            }
            FamilyEntity family = familyMapper.selectById(membership.getFamilyId());
            if (family == null || !ACTIVE.equals(family.getStatus())) {
                return null;
            }
            return new CurrentUser(
                user.getId(),
                membership.getFamilyId(),
                Role.valueOf(membership.getRole()),
                user.getTokenVersion()
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Long selectedFamilyId(HttpServletRequest request) {
        String value = request.getHeader("X-Family-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        Long familyId = Long.valueOf(value);
        FamilyEntity family = familyMapper.selectById(familyId);
        if (family == null || !ACTIVE.equals(family.getStatus())) {
            throw new IllegalArgumentException("家庭不存在或已停用");
        }
        return familyId;
    }
}
