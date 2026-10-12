package com.ethan.familyadmin.auth.dto;

import com.ethan.familyadmin.security.Role;

public record CurrentUserResponse(
    Long id,
    Long familyId,
    String username,
    String displayName,
    Role role
) {
}

