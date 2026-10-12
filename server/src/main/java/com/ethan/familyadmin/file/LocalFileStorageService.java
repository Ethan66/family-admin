package com.ethan.familyadmin.file;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Set;

@Service
public class LocalFileStorageService {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private final Path root;
    private final FileObjectMapper mapper;
    private final TenantAccessGuard accessGuard = new TenantAccessGuard();

    public LocalFileStorageService(@Value("${family-admin.storage.root}") String root, FileObjectMapper mapper) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileObjectResponse store(MultipartFile file) {
        if (file.isEmpty() || !ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只支持 JPG、PNG、WebP 或 GIF 图片");
        }
        CurrentUser user = TenantContext.requiredUser();
        accessGuard.requireBusinessCreate(user);
        Long familyId = TenantContext.requiredFamilyId();
        String extension = safeExtension(file.getOriginalFilename());
        String storageKey = familyId + "/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
        Path target = resolveSafe(storageKey);
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            FileObjectEntity entity = new FileObjectEntity();
            entity.setFamilyId(familyId);
            entity.setOriginalName(file.getOriginalFilename() == null ? "image" + extension : file.getOriginalFilename());
            entity.setStorageKey(storageKey.replace('\\', '/'));
            entity.setMediaType(file.getContentType());
            entity.setSizeBytes((long) bytes.length);
            entity.setSha256(sha256(bytes));
            entity.setStatus("ACTIVE");
            entity.setCreatedBy(user.userId());
            entity.setUpdatedBy(user.userId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            entity.setDeleted(0);
            mapper.insert(entity);
            return response(entity);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    @Transactional(readOnly = true)
    public LoadedFile load(Long id) {
        FileObjectEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        accessGuard.requireRead(TenantContext.requiredUser(), entity);
        Path path = resolveSafe(entity.getStorageKey());
        if (!Files.exists(path)) throw new BusinessException(ErrorCode.NOT_FOUND);
        return new LoadedFile(path, entity.getOriginalName(), entity.getMediaType());
    }

    private Path resolveSafe(String storageKey) {
        Path result = root.resolve(storageKey).normalize();
        if (!result.startsWith(root)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return result;
    }

    private String safeExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || name.length() - dot > 10) return "";
        String extension = name.substring(dot).toLowerCase();
        return extension.matches("\\.[a-z0-9]+") ? extension : "";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private FileObjectResponse response(FileObjectEntity entity) {
        return new FileObjectResponse(entity.getId(), entity.getOriginalName(), entity.getStorageKey(),
            entity.getMediaType(), entity.getSizeBytes());
    }
}
