package com.ethan.familyadmin.security;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TenantAccessGuard {

    public boolean canRead(CurrentUser user, TenantOwnedEntity entity) {
        return user.role() == Role.SUPER_ADMIN || Objects.equals(user.familyId(), entity.getFamilyId());
    }

    public boolean canWrite(CurrentUser user, TenantOwnedEntity entity) {
        if (!Objects.equals(user.familyId(), entity.getFamilyId())) {
            return false;
        }
        return user.role() == Role.SUPER_ADMIN
            || user.role() == Role.FAMILY_MEMBER && Objects.equals(user.userId(), entity.getCreatedBy());
    }

    public void requireRead(CurrentUser user, TenantOwnedEntity entity) {
        if (!canRead(user, entity)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    public void requireWrite(CurrentUser user, TenantOwnedEntity entity) {
        if (!canWrite(user, entity)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void requireBusinessCreate(CurrentUser user) {
        if (user.familyId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
