package com.ethan.familyadmin.audit;

import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void record(String action, String resourceType, String resourceId, Long familyId) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setFamilyId(familyId);
        entity.setOperatorId(TenantContext.requiredUser().userId());
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setResult("SUCCESS");
        entity.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(entity);
    }
}

