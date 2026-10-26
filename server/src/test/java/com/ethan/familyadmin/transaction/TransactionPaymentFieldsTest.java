package com.ethan.familyadmin.transaction;

import com.ethan.familyadmin.security.*;
import com.ethan.familyadmin.transaction.dto.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionPaymentFieldsTest {
    @Mock TransactionMapper mapper;
    TransactionService service;

    @BeforeEach void setUp() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        service = new TransactionService(mapper, new TenantAccessGuard(), mock(com.ethan.familyadmin.family.UserMapper.class));
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void createPreservesSelectionsInStorageAndResponse() {
        var response = service.create(request("招商", "京东"));
        var captor = org.mockito.ArgumentCaptor.forClass(TransactionEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getBankCard()).isEqualTo("招商");
        assertThat(captor.getValue().getPlatform()).isEqualTo("京东");
        assertThat(response.bankCard()).isEqualTo("招商");
        assertThat(response.platform()).isEqualTo("京东");
    }

    @Test void updateClearsBothFieldsAndIncludesNullsInUpdateSql() {
        var entity = new TransactionEntity();
        entity.setId(9L); entity.setFamilyId(31L); entity.setCreatedBy(7L);
        entity.setBankCard("招商"); entity.setPlatform("京东");
        when(mapper.selectById(9L)).thenReturn(entity);
        var response = service.update(9L, request("", null));
        verify(mapper).updateById(entity);
        assertThat(entity.getBankCard()).isNull();
        assertThat(entity.getPlatform()).isNull();
        assertThat(response.bankCard()).isNull();
        assertThat(response.platform()).isNull();
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var table = com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "payment-test"), TransactionEntity.class);
        assertThat(table.getAllSqlSet(false, "et.")).contains("bank_card=#{et.bankCard}", "platform=#{et.platform}");
        assertThat(table.getFieldList().stream().filter(field -> field.getProperty().equals("bankCard") || field.getProperty().equals("platform")))
            .allMatch(field -> field.getUpdateStrategy() == com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS);
    }

    @Test void validatesOnlyKnownChoicesAndAllowsOmittedFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request(null, null))).isEmpty();
            assertThat(validator.validate(request("", ""))).isEmpty();
            for (var bank : new String[]{"工商", "农业", "交通", "光大", "招商", "支付宝", "微信"}) {
                assertThat(validator.validate(request(bank, "淘宝"))).isEmpty();
            }
            for (var platform : new String[]{"支付宝", "微信", "京东", "美团", "拼多多", "淘宝", "抖音"}) {
                assertThat(validator.validate(request("工商", platform))).isEmpty();
            }
            assertThat(validator.validate(request("无效银行卡", "无效平台")))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactlyInAnyOrder("bankCard", "platform");
        }
    }

    @Test @SuppressWarnings("unchecked") void listAppliesBothFiltersWithinTheCurrentFamily() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), TransactionEntity.class);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.list(new TransactionQuery("2026-10", null, null, null, "招商", "京东", 1, 20));
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("family_id =", "bank_card =", "platform =");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(31L).containsValue("招商").containsValue("京东");
    }

    @Test void summaryUsesTheSamePaymentFilters() {
        var aggregate = new TransactionAggregate();
        aggregate.setIncome(new BigDecimal("100")); aggregate.setExpense(BigDecimal.ZERO);
        aggregate.setIncomeCount(1L); aggregate.setExpenseCount(0L);
        when(mapper.selectAggregate(31L, null, null, null, "招商", "京东", null, null)).thenReturn(aggregate);
        assertThat(service.summary(new TransactionQuery(null, null, null, null, "招商", "京东", 1, 20)).income()).isEqualByComparingTo("100");
    }

    private CreateTransactionRequest request(String bankCard, String platform) {
        return new CreateTransactionRequest(TransactionType.INCOME, new BigDecimal("100"), LocalDate.of(2026, 10, 25), "工资", "爸爸", null, bankCard, platform);
    }
}
