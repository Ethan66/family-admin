package com.ethan.familyadmin.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.exam.dto.CreateExamRequest;
import com.ethan.familyadmin.exam.dto.ExamResponse;
import com.ethan.familyadmin.exam.dto.ReviewExamRequest;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ExamService {
    private final ExamMapper mapper;
    private final TenantAccessGuard accessGuard;

    public ExamService(ExamMapper mapper, TenantAccessGuard accessGuard) {
        this.mapper = mapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(rollbackFor = Exception.class)
    public ExamResponse create(CreateExamRequest request) {
        CurrentUser user = TenantContext.requiredUser();
        accessGuard.requireBusinessCreate(user);
        ExamEntity entity = new ExamEntity();
        entity.setFamilyId(TenantContext.requiredFamilyId());
        entity.setCreatedBy(user.userId());
        entity.setUpdatedBy(user.userId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setDeleted(0);
        entity.setStatus(ExamStatus.PENDING.name());
        apply(entity, request);
        mapper.insert(entity);
        return response(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> list(String subject, String grade, ExamStatus status, long page, long pageSize) {
        var wrapper = new LambdaQueryWrapper<ExamEntity>()
            .eq(ExamEntity::getFamilyId, TenantContext.requiredFamilyId())
            .eq(subject != null && !subject.isBlank(), ExamEntity::getSubject, subject)
            .eq(grade != null && !grade.isBlank(), ExamEntity::getGrade, grade)
            .eq(status != null, ExamEntity::getStatus, status == null ? null : status.name())
            .orderByDesc(ExamEntity::getExamDate).orderByDesc(ExamEntity::getId);
        var result = mapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExamResponse update(Long id, CreateExamRequest request) {
        ExamEntity entity = required(id);
        CurrentUser user = TenantContext.requiredUser();
        accessGuard.requireWrite(user, entity);
        apply(entity, request);
        entity.setUpdatedBy(user.userId());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExamResponse review(Long id, ReviewExamRequest request) {
        ExamEntity entity = required(id);
        CurrentUser user = TenantContext.requiredUser();
        accessGuard.requireWrite(user, entity);
        if (entity.getFullScore() != null && request.score().compareTo(entity.getFullScore()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "得分不能超过满分");
        }
        entity.setScore(request.score());
        entity.setReviewNote(request.reviewNote());
        entity.setStatus(ExamStatus.REVIEWED.name());
        entity.setUpdatedBy(user.userId());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExamEntity entity = required(id);
        accessGuard.requireWrite(TenantContext.requiredUser(), entity);
        mapper.deleteById(id);
    }

    private ExamEntity required(Long id) {
        ExamEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        accessGuard.requireRead(TenantContext.requiredUser(), entity);
        return entity;
    }

    private void apply(ExamEntity entity, CreateExamRequest request) {
        entity.setName(request.name());
        entity.setSubject(request.subject());
        entity.setGrade(request.grade());
        entity.setExamDate(request.examDate());
        entity.setFullScore(request.fullScore());
    }

    private ExamResponse response(ExamEntity entity) {
        return new ExamResponse(entity.getId(), entity.getName(), entity.getSubject(), entity.getGrade(),
            entity.getExamDate(), entity.getFullScore(), entity.getScore(), ExamStatus.valueOf(entity.getStatus()),
            entity.getReviewNote(), entity.getCreatedBy(), entity.getCreatedAt());
    }
}
