package com.ethan.familyadmin.photo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.file.FileObjectEntity;
import com.ethan.familyadmin.file.FileObjectMapper;
import com.ethan.familyadmin.photo.dto.CreatePhotoRequest;
import com.ethan.familyadmin.photo.dto.PhotoResponse;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PhotoService {
    private final PhotoMapper mapper;
    private final FileObjectMapper files;
    private final TenantAccessGuard guard;

    public PhotoService(PhotoMapper mapper, FileObjectMapper files, TenantAccessGuard guard) {
        this.mapper = mapper;
        this.files = files;
        this.guard = guard;
    }

    @Transactional(rollbackFor = Exception.class)
    public PhotoResponse create(CreatePhotoRequest request) {
        CurrentUser user = TenantContext.requiredUser();
        guard.requireBusinessCreate(user);
        FileObjectEntity file = files.selectById(request.fileId());
        if (file == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        guard.requireRead(user, file);
        if (!TenantContext.requiredFamilyId().equals(file.getFamilyId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        PhotoEntity entity = new PhotoEntity();
        entity.setFamilyId(user.familyId());
        entity.setCreatedBy(user.userId());
        entity.setUpdatedBy(user.userId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setDeleted(0);
        apply(entity, request);
        mapper.insert(entity);
        return response(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<PhotoResponse> list(String album, String keyword, long page, long pageSize) {
        var wrapper = new LambdaQueryWrapper<PhotoEntity>()
            .eq(PhotoEntity::getFamilyId, TenantContext.requiredFamilyId())
            .eq(album != null && !album.isBlank(), PhotoEntity::getAlbum, album)
            .like(keyword != null && !keyword.isBlank(), PhotoEntity::getTitle, keyword)
            .orderByDesc(PhotoEntity::getPhotoDate).orderByDesc(PhotoEntity::getId);
        var result = mapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public PhotoResponse update(Long id, CreatePhotoRequest request) {
        PhotoEntity entity = required(id);
        CurrentUser user = TenantContext.requiredUser();
        guard.requireWrite(user, entity);
        FileObjectEntity file = files.selectById(request.fileId());
        if (file == null || !entity.getFamilyId().equals(file.getFamilyId())) throw new BusinessException(ErrorCode.NOT_FOUND);
        apply(entity, request);
        entity.setUpdatedBy(user.userId());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PhotoEntity entity = required(id);
        guard.requireWrite(TenantContext.requiredUser(), entity);
        mapper.deleteById(id);
    }

    private PhotoEntity required(Long id) {
        PhotoEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        guard.requireRead(TenantContext.requiredUser(), entity);
        return entity;
    }

    private void apply(PhotoEntity entity, CreatePhotoRequest request) {
        entity.setTitle(request.title()); entity.setAlbum(request.album()); entity.setPhotoDate(request.photoDate());
        entity.setDescription(request.description()); entity.setFileId(request.fileId());
    }

    private PhotoResponse response(PhotoEntity e) {
        return new PhotoResponse(e.getId(), e.getTitle(), e.getAlbum(), e.getPhotoDate(), e.getDescription(),
            e.getFileId(), e.getCreatedBy(), e.getCreatedAt());
    }
}
