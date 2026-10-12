package com.ethan.familyadmin.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final OperationLogMapper mapper;
    public AuditQueryService(OperationLogMapper mapper) { this.mapper = mapper; }
    @Transactional(readOnly = true)
    public PageResponse<OperationLogResponse> list(Long familyId, long page, long pageSize) {
        var result = mapper.selectPage(Page.of(page, pageSize), new LambdaQueryWrapper<OperationLogEntity>()
            .eq(familyId != null, OperationLogEntity::getFamilyId, familyId)
            .orderByDesc(OperationLogEntity::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, pageSize);
    }
    private OperationLogResponse response(OperationLogEntity e) { return new OperationLogResponse(e.getId(),e.getFamilyId(),e.getOperatorId(),e.getAction(),e.getResourceType(),e.getResourceId(),e.getResult(),e.getCreatedAt()); }
}
