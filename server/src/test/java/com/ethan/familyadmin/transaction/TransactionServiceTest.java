package com.ethan.familyadmin.transaction;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.family.UserMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Mock
    private UserMapper userMapper;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionMapper, new TenantAccessGuard(), userMapper);
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
            "早餐", null, null
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
        when(transactionMapper.selectAggregate(31L, null, null, null, null, null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))).thenReturn(aggregate);

        TransactionSummaryResponse summary = service.summary(new TransactionQuery("2026-09", null, null, null, null, null, 1, 20));

        assertThat(summary.expense()).isEqualByComparingTo("1210.00");
        assertThat(summary.income()).isEqualByComparingTo("5000.00");
        assertThat(summary.balance()).isEqualByComparingTo("3790.00");
        assertThat(summary.incomeCount()).isEqualTo(1);
        assertThat(summary.expenseCount()).isEqualTo(2);
        verify(transactionMapper, never()).selectList(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersByAttributionNameWithinCurrentFamily() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), TransactionEntity.class);
        when(transactionMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.list(new TransactionQuery("2026-10", TransactionType.EXPENSE, "餐饮", "妈妈", null, null, 1, 20));

        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(transactionMapper).selectPage(any(), captor.capture());
        var wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("family_id =", "member_name =", "category =", "transaction_date >=");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(31L).containsValue("妈妈").containsValue("餐饮");
    }

    @Test
    void summaryPassesTypeAndCategoryFiltersToAggregate() {
        when(transactionMapper.selectAggregate(31L, "EXPENSE", "餐饮", "爸爸", null, null, null, null))
            .thenReturn(aggregate("0.00", "12.30", 0L, 1L));

        TransactionSummaryResponse summary = service.summary(
            new TransactionQuery(null, TransactionType.EXPENSE, "餐饮", "爸爸", null, null, 1, 20));

        assertThat(summary.expense()).isEqualByComparingTo("12.30");
        assertThat(summary.income()).isEqualByComparingTo("0.00");
        verify(transactionMapper).selectAggregate(31L, "EXPENSE", "餐饮", "爸爸", null, null, null, null);
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

    @Test
    void selectedMemberCanUpdateRecordCreatedByAnotherMember() {
        selectedMemberRecord(31L);
        service.update(9L, new CreateTransactionRequest(TransactionType.EXPENSE, new BigDecimal("3.00"),
            LocalDate.of(2026, 10, 26), "餐饮", "zc", "修改", null, null));
        verify(transactionMapper).updateById(any(TransactionEntity.class));
    }

    @Test
    void selectedMemberCanDeleteRecordCreatedByAnotherMember() {
        selectedMemberRecord(31L);
        LocalDateTime beforeDelete = LocalDateTime.now();
        service.delete(9L);
        var captor = org.mockito.ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionMapper).deleteById(captor.capture());
        TransactionEntity deleted = captor.getValue();
        assertThat(deleted.getId()).isEqualTo(9L);
        assertThat(deleted.getCreatedBy()).isEqualTo(8L);
        assertThat(deleted.getUpdatedBy()).isEqualTo(7L);
        assertThat(deleted.getUpdatedAt()).isBetween(beforeDelete, LocalDateTime.now());

        // 使用实际映射检查逻辑删除的参数，避免仅模拟 Mapper 漏掉数据库空值问题。
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(TransactionMapper.class);
        var boundSql = configuration.getMappedStatement(TransactionMapper.class.getName() + ".deleteById")
            .getBoundSql(deleted);
        assertThat(boundSql.getSql()).contains("UPDATE transaction_record", "updated_by=", "updated_at=", "deleted=1");
        assertThat(boundSql.getParameterMappings()).extracting(org.apache.ibatis.mapping.ParameterMapping::getProperty)
            .contains("updatedBy", "updatedAt", "id");
        var parameters = configuration.newMetaObject(deleted);
        for (var mapping : boundSql.getParameterMappings()) {
            assertThat(parameters.getValue(mapping.getProperty())).as(mapping.getProperty()).isNotNull();
        }
        verify(transactionMapper, never()).deleteById(9L);
    }

    @Test
    void unrelatedMemberCannotUpdateOrDeleteSelectedMembersRecord() {
        selectedMemberRecord(31L);
        userMapper.selectById(7L).setDisplayName("xi");
        assertThatThrownBy(() -> service.update(9L, new CreateTransactionRequest(TransactionType.EXPENSE,
            BigDecimal.ONE, LocalDate.of(2026, 10, 26), "餐饮", "xi", null, null, null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(BusinessException.class);
        verify(transactionMapper, never()).updateById(any(TransactionEntity.class));
        verify(transactionMapper, never()).deleteById(9L);
        verify(transactionMapper, never()).deleteById(any(TransactionEntity.class));
    }

    @Test
    void selectedMemberCannotModifyRecordFromAnotherFamily() {
        TransactionEntity record = entity(TransactionType.EXPENSE, "3.00");
        record.setId(9L);
        record.setFamilyId(32L);
        record.setMemberName("zc");
        when(transactionMapper.selectById(9L)).thenReturn(record);
        assertThatThrownBy(() -> service.update(9L, new CreateTransactionRequest(TransactionType.EXPENSE,
            BigDecimal.ONE, LocalDate.of(2026, 10, 26), "餐饮", "zc", null, null, null)))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(BusinessException.class);
        verify(transactionMapper, never()).updateById(any(TransactionEntity.class));
        verify(transactionMapper, never()).deleteById(9L);
        verify(transactionMapper, never()).deleteById(any(TransactionEntity.class));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void creatorAndAdministratorRetainWritePermission(Role role) {
        TenantContext.set(new CurrentUser(7L, 31L, role, 0));
        TransactionEntity record = entity(TransactionType.EXPENSE, "3.00");
        record.setId(9L);
        record.setMemberName("xi");
        if (role == Role.SUPER_ADMIN) record.setCreatedBy(8L);
        when(transactionMapper.selectById(9L)).thenReturn(record);
        service.update(9L, new CreateTransactionRequest(TransactionType.EXPENSE, BigDecimal.ONE,
            LocalDate.of(2026, 10, 26), "餐饮", "xi", null, null, null));
        service.delete(9L);
        verify(transactionMapper).updateById(any(TransactionEntity.class));
        verify(transactionMapper).deleteById(record);
        assertThat(record.getUpdatedBy()).isEqualTo(7L);
        assertThat(record.getUpdatedAt()).isNotNull();
        verify(userMapper, never()).selectById(any());
    }

    private void selectedMemberRecord(Long familyId) {
        TransactionEntity record = entity(TransactionType.EXPENSE, "3.00");
        record.setId(9L);
        record.setFamilyId(familyId);
        record.setCreatedBy(8L);
        record.setMemberName("zc");
        when(transactionMapper.selectById(9L)).thenReturn(record);
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDisplayName("zc");
        when(userMapper.selectById(7L)).thenReturn(user);
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
