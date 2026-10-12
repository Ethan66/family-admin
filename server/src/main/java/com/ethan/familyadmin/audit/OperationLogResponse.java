package com.ethan.familyadmin.audit;

import java.time.LocalDateTime;

public record OperationLogResponse(Long id, Long familyId, Long operatorId, String action,
                                   String resourceType, String resourceId, String result, LocalDateTime createdAt) { }
