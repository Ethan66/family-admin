package com.ethan.familyadmin.file;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalFileStorageServiceTest {
    @TempDir Path root;
    private final FileObjectMapper mapper = mock(FileObjectMapper.class);

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void uploadStoresFileUnderFamilyDirectory() {
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        doAnswer(call -> { ((FileObjectEntity) call.getArgument(0)).setId(33L); return 1; })
            .when(mapper).insert(any(FileObjectEntity.class));
        var service = new LocalFileStorageService(root.toString(), mapper);

        var result = service.store(new MockMultipartFile("file", "pic.jpg", "image/jpeg", "image".getBytes()));

        assertThat(result.id()).isEqualTo(33L);
        assertThat(result.storageKey()).startsWith("12/");
        assertThat(root.resolve(result.storageKey())).exists();
    }

    @Test
    void downloadRejectsAnotherFamilyFile() {
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        FileObjectEntity entity = new FileObjectEntity();
        entity.setId(1L);
        entity.setFamilyId(99L);
        entity.setStorageKey("99/a.jpg");
        when(mapper.selectById(1L)).thenReturn(entity);
        var service = new LocalFileStorageService(root.toString(), mapper);

        assertThatThrownBy(() -> service.load(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void uploadRejectsSvgBecauseItMayContainActiveContent() {
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        var service = new LocalFileStorageService(root.toString(), mapper);

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
            "file", "unsafe.svg", "image/svg+xml", "<svg><script/></svg>".getBytes()
        ))).isInstanceOf(BusinessException.class);
    }
}
