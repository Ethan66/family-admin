package com.ethan.familyadmin.dashboard;

import com.ethan.familyadmin.exam.ExamMapper;
import com.ethan.familyadmin.movie.MovieMapper;
import com.ethan.familyadmin.note.StudyNoteMapper;
import com.ethan.familyadmin.photo.PhotoMapper;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantContext;
import com.ethan.familyadmin.transaction.TransactionEntity;
import com.ethan.familyadmin.transaction.TransactionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void aggregatesCurrentFamilyData() {
        TransactionMapper transactions = mock(TransactionMapper.class);
        TransactionEntity income = record("INCOME", "5000");
        TransactionEntity expense = record("EXPENSE", "1200");
        when(transactions.selectList(any())).thenReturn(List.of(income, expense));
        ExamMapper exams = mock(ExamMapper.class);
        PhotoMapper photos = mock(PhotoMapper.class);
        MovieMapper movies = mock(MovieMapper.class);
        StudyNoteMapper notes = mock(StudyNoteMapper.class);
        when(exams.selectCount(any())).thenReturn(3L);
        when(photos.selectCount(any())).thenReturn(8L);
        when(movies.selectCount(any())).thenReturn(5L);
        when(notes.selectCount(any())).thenReturn(6L);
        TenantContext.set(new CurrentUser(1L, 9L, Role.FAMILY_MEMBER, 0));

        var result = new DashboardService(transactions, exams, photos, movies, notes).overview();

        assertThat(result.income()).isEqualByComparingTo("5000");
        assertThat(result.expense()).isEqualByComparingTo("1200");
        assertThat(result.balance()).isEqualByComparingTo("3800");
        assertThat(result.examCount()).isEqualTo(3);
        assertThat(result.photoCount()).isEqualTo(8);
    }

    private TransactionEntity record(String type, String amount) {
        TransactionEntity entity = new TransactionEntity();
        entity.setType(type);
        entity.setAmount(new BigDecimal(amount));
        return entity;
    }
}
