package com.ethan.familyadmin.transaction;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import com.ethan.familyadmin.transaction.dto.TransactionAggregate;
import com.ethan.familyadmin.transaction.dto.CreateTransactionRequest;
import com.ethan.familyadmin.transaction.dto.TransactionQuery;
import com.ethan.familyadmin.transaction.dto.TransactionSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionMapper, new TenantAccessGuard());
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createUsesAuthenticatedFamilyAndOwner() {
        CreateTransactionRequest request = new CreateTransactionRequest(
            TransactionType.EXPENSE,
            new BigDecimal("12.30"),
            LocalDate.of(2026, 9, 23),
            "餐饮",
            "爸爸",
            "早餐"
        );

        service.create(request);

        verify(transactionMapper).insert(any(TransactionEntity.class));
        TransactionEntity inserted = captureInsertedEntity();
        assertThat(inserted.getFamilyId()).isEqualTo(31L);
        assertThat(inserted.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void summaryUsesDatabaseAggregateWithinCurrentFamily() {
        TransactionAggregate aggregate = aggregate("5000.00", "1210.00", 1L, 2L);
        when(transactionMapper.selectAggregate(31L, null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))).thenReturn(aggregate);

        TransactionSummaryResponse summary = service.summary(new TransactionQuery("2026-09", null, null, 1, 20));

        assertThat(summary.expense()).isEqualByComparingTo("1210.00");
        assertThat(summary.income()).isEqualByComparingTo("5000.00");
        assertThat(summary.balance()).isEqualByComparingTo("3790.00");
        assertThat(summary.incomeCount()).isEqualTo(1);
        assertThat(summary.expenseCount()).isEqualTo(2);
        verify(transactionMapper, never()).selectList(any());
    }

    @Test
    void summaryPassesTypeAndCategoryFiltersToAggregate() {
        when(transactionMapper.selectAggregate(31L, "EXPENSE", "餐饮", null, null))
            .thenReturn(aggregate("0.00", "12.30", 0L, 1L));

        TransactionSummaryResponse summary = service.summary(
            new TransactionQuery(null, TransactionType.EXPENSE, "餐饮", 1, 20));

        assertThat(summary.expense()).isEqualByComparingTo("12.30");
        assertThat(summary.income()).isEqualByComparingTo("0.00");
        verify(transactionMapper).selectAggregate(31L, "EXPENSE", "餐饮", null, null);
    }

    private TransactionAggregate aggregate(String income, String expense, long incomeCount, long expenseCount) {
        TransactionAggregate aggregate = new TransactionAggregate();
        aggregate.setIncome(new BigDecimal(income));
        aggregate.setExpense(new BigDecimal(expense));
        aggregate.setIncomeCount(incomeCount);
        aggregate.setExpenseCount(expenseCount);
        return aggregate;
    }

    @Test
    void memberCannotDeleteRecordCreatedByAnotherMember() {
        TransactionEntity entity = entity(TransactionType.EXPENSE, "10.00");
        entity.setId(9L);
        entity.setCreatedBy(8L);
        when(transactionMapper.selectById(9L)).thenReturn(entity);

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(BusinessException.class);
        verify(transactionMapper, never()).deleteById(9L);
    }

    private TransactionEntity captureInsertedEntity() {
        var captor = org.mockito.ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private TransactionEntity entity(TransactionType type, String amount) {
        TransactionEntity entity = new TransactionEntity();
        entity.setFamilyId(31L);
        entity.setCreatedBy(7L);
        entity.setType(type.name());
        entity.setAmount(new BigDecimal(amount));
        return entity;
    }
}
