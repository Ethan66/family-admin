package com.ethan.familyadmin.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @Test
    void familyUserContextProvidesMembershipFamilyId() {
        CurrentUser user = new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 2);

        TenantContext.runAs(user, () -> assertThat(TenantContext.requiredFamilyId()).isEqualTo(31L));
    }

    @Test
    void runAsAlwaysClearsContext() {
        CurrentUser user = new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 2);

        assertThatThrownBy(() -> TenantContext.runAs(user, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(TenantContext::requiredUser)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("认证上下文");
    }
}

