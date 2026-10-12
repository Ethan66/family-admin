package com.ethan.familyadmin.note;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyNoteService {
    private final StudyNoteMapper mapper; private final TenantAccessGuard guard;
    public StudyNoteService(StudyNoteMapper mapper, TenantAccessGuard guard) { this.mapper = mapper; this.guard = guard; }
    @Transactional(readOnly = true)
    public PageResponse<StudyNoteResponse> list(String tag, String keyword, long page, long pageSize) {
        var wrapper = new LambdaQueryWrapper<StudyNoteEntity>().eq(StudyNoteEntity::getFamilyId, TenantContext.requiredFamilyId())
            .eq(tag != null && !tag.isBlank(), StudyNoteEntity::getTag, tag)
            .and(keyword != null && !keyword.isBlank(), w -> w.like(StudyNoteEntity::getTitle, keyword).or().like(StudyNoteEntity::getMarkdownBody, keyword))
            .orderByDesc(StudyNoteEntity::getUpdatedAt);
        var result = mapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, pageSize);
    }
    @Transactional(readOnly = true)
    public StudyNoteResponse get(Long id) { return response(required(id)); }
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) { StudyNoteEntity entity = required(id); guard.requireWrite(TenantContext.requiredUser(), entity); mapper.deleteById(id); }
    private StudyNoteEntity required(Long id) { StudyNoteEntity e = mapper.selectById(id); if (e == null) throw new BusinessException(ErrorCode.NOT_FOUND); guard.requireRead(TenantContext.requiredUser(), e); return e; }
    private StudyNoteResponse response(StudyNoteEntity e) { return new StudyNoteResponse(e.getId(), e.getTitle(), e.getTag(), e.getMarkdownBody(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()); }
}
