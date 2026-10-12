package com.ethan.familyadmin.photo;

import com.ethan.familyadmin.file.FileObjectEntity;
import com.ethan.familyadmin.file.FileObjectMapper;
import com.ethan.familyadmin.photo.dto.CreatePhotoRequest;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhotoServiceTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    void createRequiresFileFromSameFamily() {
        PhotoMapper photos = mock(PhotoMapper.class);
        FileObjectMapper files = mock(FileObjectMapper.class);
        FileObjectEntity file = new FileObjectEntity();
        file.setId(5L);
        file.setFamilyId(12L);
        when(files.selectById(5L)).thenReturn(file);
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        PhotoService service = new PhotoService(photos, files, new TenantAccessGuard());

        var result = service.create(new CreatePhotoRequest("秋游", "家庭", LocalDate.of(2026, 9, 20), "公园", 5L));

        verify(photos).insert(any(PhotoEntity.class));
        assertThat(result.fileId()).isEqualTo(5L);
    }
}
