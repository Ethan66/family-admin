package com.ethan.familyadmin.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ethan.familyadmin.exam.ExamEntity;
import com.ethan.familyadmin.exam.ExamMapper;
import com.ethan.familyadmin.movie.MovieEntity;
import com.ethan.familyadmin.movie.MovieMapper;
import com.ethan.familyadmin.note.StudyNoteEntity;
import com.ethan.familyadmin.note.StudyNoteMapper;
import com.ethan.familyadmin.photo.PhotoEntity;
import com.ethan.familyadmin.photo.PhotoMapper;
import com.ethan.familyadmin.security.TenantContext;
import com.ethan.familyadmin.transaction.TransactionEntity;
import com.ethan.familyadmin.transaction.TransactionMapper;
import com.ethan.familyadmin.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
public class DashboardService {
    private final TransactionMapper transactions;
    private final ExamMapper exams;
    private final PhotoMapper photos;
    private final MovieMapper movies;
    private final StudyNoteMapper notes;

    public DashboardService(TransactionMapper transactions, ExamMapper exams, PhotoMapper photos,
                            MovieMapper movies, StudyNoteMapper notes) {
        this.transactions = transactions;
        this.exams = exams;
        this.photos = photos;
        this.movies = movies;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public DashboardOverview overview() {
        Long familyId = TenantContext.requiredFamilyId();
        YearMonth month = YearMonth.now();
        var records = transactions.selectList(new LambdaQueryWrapper<TransactionEntity>()
            .eq(TransactionEntity::getFamilyId, familyId)
            .ge(TransactionEntity::getTransactionDate, month.atDay(1))
            .lt(TransactionEntity::getTransactionDate, month.plusMonths(1).atDay(1)));
        BigDecimal income = sum(records, TransactionType.INCOME.name());
        BigDecimal expense = sum(records, TransactionType.EXPENSE.name());
        return new DashboardOverview(income, expense, income.subtract(expense),
            exams.selectCount(new LambdaQueryWrapper<ExamEntity>().eq(ExamEntity::getFamilyId, familyId)),
            photos.selectCount(new LambdaQueryWrapper<PhotoEntity>().eq(PhotoEntity::getFamilyId, familyId)),
            movies.selectCount(new LambdaQueryWrapper<MovieEntity>().eq(MovieEntity::getFamilyId, familyId)),
            notes.selectCount(new LambdaQueryWrapper<StudyNoteEntity>().eq(StudyNoteEntity::getFamilyId, familyId)));
    }

    private BigDecimal sum(java.util.List<TransactionEntity> records, String type) {
        return records.stream().filter(item -> type.equals(item.getType())).map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
