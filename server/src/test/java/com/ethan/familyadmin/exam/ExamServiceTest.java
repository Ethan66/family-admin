package com.ethan.familyadmin.exam;

import com.ethan.familyadmin.exam.dto.CreateExamRequest;
import com.ethan.familyadmin.exam.dto.ReviewExamRequest;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamServiceTest {

    private final ExamMapper mapper = mock(ExamMapper.class);
    private final ExamService service = new ExamService(mapper, new TenantAccessGuard());

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void createUsesCurrentFamilyAndInitialPendingStatus() {
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        CreateExamRequest request = new CreateExamRequest(
            "九月数学测验", "数学", "三年级", LocalDate.of(2026, 9, 20), new BigDecimal("100")
        );

        service.create(request);

        verify(mapper).insert(any(ExamEntity.class));
        var entity = org.mockito.ArgumentCaptor.forClass(ExamEntity.class);
        verify(mapper).insert(entity.capture());
        assertThat(entity.getValue().getFamilyId()).isEqualTo(12L);
        assertThat(entity.getValue().getStatus()).isEqualTo(ExamStatus.PENDING.name());
    }

    @Test
    void ownerCanReviewExam() {
        TenantContext.set(new CurrentUser(7L, 12L, Role.FAMILY_MEMBER, 0));
        ExamEntity entity = new ExamEntity();
        entity.setId(2L);
        entity.setFamilyId(12L);
        entity.setCreatedBy(7L);
        entity.setFullScore(new BigDecimal("100"));
        when(mapper.selectById(2L)).thenReturn(entity);

        var result = service.review(2L, new ReviewExamRequest(new BigDecimal("92"), "基础扎实"));

        assertThat(result.status()).isEqualTo(ExamStatus.REVIEWED);
        assertThat(result.score()).isEqualByComparingTo("92");
        verify(mapper).updateById(entity);
    }
}
