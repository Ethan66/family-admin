package com.ethan.familyadmin.quicknote;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.file.FileObjectEntity;
import com.ethan.familyadmin.file.FileObjectMapper;
import com.ethan.familyadmin.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuickNoteServiceTest {
    private final QuickNoteMapper mapper = mock(QuickNoteMapper.class);
    private final FileObjectMapper files = mock(FileObjectMapper.class);
    private final QuickNoteService service = new QuickNoteService(mapper, files, new ObjectMapper(), new TenantAccessGuard());

    @BeforeEach void login() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), QuickNoteEntity.class);
        TenantContext.set(new CurrentUser(2L, 8L, Role.FAMILY_MEMBER, 0));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    private QuickNotePayload payload(String body) {
        return new QuickNotePayload("", body, List.of("生活"), List.of(), List.of(), "plain", false, "ACTIVE", 0);
    }
    private QuickNoteEntity entity() {
        var e = new QuickNoteEntity(); e.setId(4L); e.setFamilyId(8L); e.setCreatedBy(2L);
        e.setTitle(""); e.setBody("原内容"); e.setTagsJson("[]"); e.setImageIdsJson("[]");
        e.setTasksJson("[]"); e.setColor("plain"); e.setPinned(false); e.setStatus("ACTIVE"); e.setVersion(0);
        when(mapper.selectById(4L)).thenReturn(e); return e;
    }

    @Test void createsStructuredNoteWithAuditFields() {
        when(mapper.insert(any(QuickNoteEntity.class))).thenAnswer(call -> { call.<QuickNoteEntity>getArgument(0).setId(9L); return 1; });
        var res = service.create(payload("记录一下"));
        assertThat(res.body()).isEqualTo("记录一下"); assertThat(res.createdBy()).isEqualTo(2L);
        assertThat(res.tags()).containsExactly("生活"); assertThat(res.version()).isZero();
        verify(mapper).insert(argThat((QuickNoteEntity e) -> e.getFamilyId() == 8L && e.getCreatedAt() != null && e.getUpdatedBy() == 2L));
    }

    @Test void rejectsEmptyNote() {
        assertThatThrownBy(() -> service.create(payload("  "))).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(QuickNoteEntity.class));
    }

    @Test void rejectsAnotherFamiliesImageEvenForAdministrator() {
        TenantContext.set(new CurrentUser(1L, 8L, Role.SUPER_ADMIN, 0));
        var file = new FileObjectEntity(); file.setFamilyId(9L); file.setMediaType("image/png"); file.setStatus("ACTIVE");
        when(files.selectById(10L)).thenReturn(file);
        var p = new QuickNotePayload("", "图片", List.of(), List.of(10L), List.of(), "plain", false, "ACTIVE", 0);
        assertThatThrownBy(() -> service.create(p)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(QuickNoteEntity.class));
    }

    @Test void refusesWritingAnotherAuthorsNote() {
        entity().setCreatedBy(3L);
        assertThatThrownBy(() -> service.update(4L, payload("修改"))).isInstanceOf(BusinessException.class);
        verify(mapper, never()).update(any(QuickNoteEntity.class), any());
    }

    @Test void refusesAccessOutsideSelectedFamilyEvenForAdministrator() {
        entity().setFamilyId(9L);
        TenantContext.set(new CurrentUser(1L, 8L, Role.SUPER_ADMIN, 0));
        assertThatThrownBy(() -> service.update(4L, payload("修改"))).isInstanceOf(BusinessException.class);
    }

    @Test void detectsStaleVersionBeforeWriting() {
        entity().setVersion(2);
        assertThatThrownBy(() -> service.update(4L, payload("修改")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR));
        verify(mapper, never()).update(any(QuickNoteEntity.class), any());
    }

    @Test void detectsConcurrentChangeDuringUpdate() {
        entity(); when(mapper.update(any(QuickNoteEntity.class), any())).thenReturn(0);
        assertThatThrownBy(() -> service.update(4L, payload("修改"))).isInstanceOf(BusinessException.class);
    }

    @Test void incrementsVersionOnSuccess() {
        entity(); when(mapper.update(any(QuickNoteEntity.class), any())).thenReturn(1);
        assertThat(service.update(4L, payload("修改")).version()).isEqualTo(1);
    }

    @Test void directlyDeletesActiveNotes() {
        entity(); when(mapper.update(isNull(), any())).thenReturn(1);
        service.delete(4L, 0); verify(mapper).update(isNull(), any());
    }

    @Test void directlyDeletesArchivedNotes() {
        entity().setStatus("ARCHIVED"); when(mapper.update(isNull(), any())).thenReturn(1);
        service.delete(4L, 0); verify(mapper).update(isNull(), any());
    }

    @Test void refusesDeletingAnotherAuthorsNote() {
        entity().setCreatedBy(3L);
        assertThatThrownBy(() -> service.delete(4L, 0)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).update(isNull(), any());
    }

    @Test void refusesDeletingAChangedNote() {
        entity().setVersion(1);
        assertThatThrownBy(() -> service.delete(4L, 0)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).update(isNull(), any());
    }

    @Test void rejectsInvalidStatusAndTooLongContent() {
        var p = new QuickNotePayload("", "内容", List.of(), List.of(), List.of(), "plain", false, "INVALID", 0);
        assertThatThrownBy(() -> service.create(p)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(payload("字".repeat(10001)))).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(QuickNoteEntity.class));
    }

    @Test void refusesRequestsWithoutASelectedFamily() {
        TenantContext.set(new CurrentUser(1L, null, Role.SUPER_ADMIN, 0));
        assertThatThrownBy(service::list).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(payload("内容"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
}
