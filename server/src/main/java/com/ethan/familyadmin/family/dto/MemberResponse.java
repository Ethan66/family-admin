package com.ethan.familyadmin.family.dto;

import com.ethan.familyadmin.security.Role;

public record MemberResponse(
    Long userId,
    Long familyId,
    String username,
    String displayName,
    String status,
    Role role
) {
}

