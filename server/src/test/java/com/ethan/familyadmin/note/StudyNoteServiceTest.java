package com.ethan.familyadmin.note;

import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class StudyNoteServiceTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    void ownerCanDeleteExistingNote() {
        StudyNoteMapper mapper = mock(StudyNoteMapper.class);
        StudyNoteEntity note = new StudyNoteEntity();
        note.setId(4L);
        note.setFamilyId(8L);
        note.setCreatedBy(2L);
        when(mapper.selectById(4L)).thenReturn(note);
        TenantContext.set(new CurrentUser(2L, 8L, Role.FAMILY_MEMBER, 0));

        new StudyNoteService(mapper, new TenantAccessGuard()).delete(4L);

        verify(mapper).deleteById(4L);
    }
}
