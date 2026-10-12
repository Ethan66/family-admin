package com.ethan.familyadmin.security;

import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAccessGuardTest {

    private final TenantAccessGuard guard = new TenantAccessGuard();

    @Test
    void memberCanWriteOnlyOwnRecordInSameFamily() {
        CurrentUser member = new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0);

        assertThat(guard.canWrite(member, entity(31L, 7L))).isTrue();
        assertThat(guard.canWrite(member, entity(31L, 8L))).isFalse();
        assertThat(guard.canWrite(member, entity(32L, 7L))).isFalse();
    }

    @Test
    void superAdminCanWriteAllRecordsInSelectedFamily() {
        CurrentUser superAdmin = new CurrentUser(1L, 31L, Role.SUPER_ADMIN, 0);

        assertThat(guard.canRead(superAdmin, entity(31L, 8L))).isTrue();
        assertThat(guard.canWrite(superAdmin, entity(31L, 8L))).isTrue();
        assertThat(guard.canWrite(superAdmin, entity(32L, 8L))).isFalse();
        guard.requireBusinessCreate(superAdmin);
    }

    @Test
    void superAdminWithoutSelectedFamilyCannotCreateBusinessData() {
        CurrentUser superAdmin = new CurrentUser(1L, null, Role.SUPER_ADMIN, 0);

        assertThatThrownBy(() -> guard.requireBusinessCreate(superAdmin))
            .isInstanceOf(com.ethan.familyadmin.common.exception.BusinessException.class);
    }

    private TenantOwnedEntity entity(Long familyId, Long createdBy) {
        return new TenantOwnedEntity() {
            @Override
            public Long getFamilyId() {
                return familyId;
            }

            @Override
            public Long getCreatedBy() {
                return createdBy;
            }
        };
    }
}
