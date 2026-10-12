package com.ethan.familyadmin.security;

public record CurrentUser(Long userId, Long familyId, Role role, int tokenVersion) {
}

