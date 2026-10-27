package com.ethan.familyadmin.quicknote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.file.FileObjectMapper;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class QuickNoteService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private final QuickNoteMapper mapper;
    private final FileObjectMapper files;
    private final ObjectMapper json;
    private final TenantAccessGuard guard;

    public QuickNoteService(QuickNoteMapper mapper, FileObjectMapper files, ObjectMapper json, TenantAccessGuard guard) {
        this.mapper = mapper; this.files = files; this.json = json; this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<QuickNoteResponse> list() {
        guard.requireBusinessCreate(TenantContext.requiredUser());
        return mapper.selectList(new LambdaQueryWrapper<QuickNoteEntity>()
            .eq(QuickNoteEntity::getFamilyId, TenantContext.requiredFamilyId())
            .in(QuickNoteEntity::getStatus, List.of("ACTIVE", "ARCHIVED"))
            .orderByDesc(QuickNoteEntity::getUpdatedAt).orderByDesc(QuickNoteEntity::getId))
            .stream().map(this::response).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public QuickNoteResponse create(QuickNotePayload payload) {
        guard.requireBusinessCreate(TenantContext.requiredUser());
        if (!"ACTIVE".equals(payload.status()) || !Objects.equals(payload.version(), 0)) invalid("新笔记必须处于正常状态");
        var entity = new QuickNoteEntity();
        entity.setFamilyId(TenantContext.requiredFamilyId());
        entity.setCreatedBy(TenantContext.requiredUser().userId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        apply(entity, payload);
        entity.setVersion(0);
        mapper.insert(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuickNoteResponse update(Long id, QuickNotePayload payload) {
        var entity = required(id);
        guard.requireWrite(TenantContext.requiredUser(), entity);
        if (!Objects.equals(entity.getVersion(), payload.version())) conflict();
        apply(entity, payload);
        entity.setVersion(payload.version() + 1);
        // 将读取时的版本一起放入写入条件，避免两个窗口相互覆盖正文或清单。
        int changed = mapper.update(entity, new LambdaUpdateWrapper<QuickNoteEntity>()
            .eq(QuickNoteEntity::getId, id).eq(QuickNoteEntity::getFamilyId, entity.getFamilyId())
            .eq(QuickNoteEntity::getVersion, payload.version()));
        if (changed != 1) conflict();
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, int version) {
        var entity = required(id);
        guard.requireWrite(TenantContext.requiredUser(), entity);
        if (entity.getVersion() != version) conflict();
        // 直接删除沿用项目的逻辑删除机制，保留操作人，并在写入时核对家庭和版本。
        int changed = mapper.update(null, new LambdaUpdateWrapper<QuickNoteEntity>()
            .set(QuickNoteEntity::getDeleted, 1)
            .set(QuickNoteEntity::getUpdatedBy, TenantContext.requiredUser().userId())
            .set(QuickNoteEntity::getUpdatedAt, LocalDateTime.now())
            .eq(QuickNoteEntity::getId, id).eq(QuickNoteEntity::getFamilyId, entity.getFamilyId())
            .eq(QuickNoteEntity::getVersion, version));
        if (changed != 1) conflict();
    }

    private QuickNoteEntity required(Long id) {
        guard.requireBusinessCreate(TenantContext.requiredUser());
        var entity = mapper.selectById(id);
        // 超级管理员也必须先切换到对应家庭，不能借 ID 跨当前家庭访问。
        if (entity == null || !Objects.equals(entity.getFamilyId(), TenantContext.requiredFamilyId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        guard.requireRead(TenantContext.requiredUser(), entity);
        return entity;
    }

    private void apply(QuickNoteEntity entity, QuickNotePayload payload) {
        if (!VALIDATOR.validate(payload).isEmpty()) invalid("笔记字段不符合要求");
        if (payload.title().isBlank() && payload.body().isBlank() && payload.imageIds().isEmpty() && payload.tasks().isEmpty()) {
            invalid("请记录一些内容后再保存");
        }
        for (Long id : payload.imageIds()) {
            var file = files.selectById(id);
            if (file == null || !Objects.equals(file.getFamilyId(), entity.getFamilyId())
                || !"ACTIVE".equals(file.getStatus()) || !IMAGE_TYPES.contains(file.getMediaType())) {
                invalid("图片不存在或不属于当前家庭");
            }
        }
        entity.setTitle(payload.title().strip());
        entity.setBody(payload.body().strip());
        entity.setTagsJson(write(payload.tags().stream().map(String::strip).distinct().toList()));
        entity.setImageIdsJson(write(payload.imageIds().stream().distinct().toList()));
        entity.setTasksJson(write(payload.tasks()));
        entity.setColor(payload.color()); entity.setPinned(payload.pinned()); entity.setStatus(payload.status());
        entity.setUpdatedBy(TenantContext.requiredUser().userId()); entity.setUpdatedAt(LocalDateTime.now());
    }

    private QuickNoteResponse response(QuickNoteEntity e) {
        return new QuickNoteResponse(e.getId(), e.getTitle(), e.getBody(), read(e.getTagsJson(), new TypeReference<>() { }),
            read(e.getImageIdsJson(), new TypeReference<>() { }), read(e.getTasksJson(), new TypeReference<>() { }),
            e.getColor(), e.getPinned(), e.getStatus(), e.getVersion(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("笔记序列化失败", e); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("笔记数据损坏", e); }
    }
    private void invalid(String message) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
    private void conflict() { throw new BusinessException(ErrorCode.BUSINESS_ERROR, "笔记已被其他操作修改，请刷新后再试"); }
}
