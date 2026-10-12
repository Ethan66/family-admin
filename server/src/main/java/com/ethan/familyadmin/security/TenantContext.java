package com.ethan.familyadmin.security;

import java.util.Objects;

public final class TenantContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static CurrentUser requiredUser() {
        CurrentUser user = CURRENT.get();
        if (user == null) {
            throw new IllegalStateException("认证上下文不存在");
        }
        return user;
    }

    public static Long requiredFamilyId() {
        return Objects.requireNonNull(requiredUser().familyId(), "当前用户没有家庭上下文");
    }

    public static void set(CurrentUser user) {
        CURRENT.set(Objects.requireNonNull(user));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(CurrentUser user, Runnable action) {
        set(user);
        try {
            action.run();
        } finally {
            clear();
        }
    }
}

