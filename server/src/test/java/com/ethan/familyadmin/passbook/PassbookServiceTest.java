package com.ethan.familyadmin.passbook;

import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PassbookServiceTest {
    @Mock PassbookMapper mapper;
    @Mock UserMapper users;
    @Mock FamilyMemberMapper members;
    PassbookService service;

    @BeforeEach void setUp() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        lenient().when(mapper.selectTotals(any())).thenReturn(new PassbookTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        service = new PassbookService(mapper, users, members, new TenantAccessGuard());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PassbookEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "member"), FamilyMemberEntity.class);
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void savesManualInterestAndDerivesMaturityAndTotal() {
        allowMember();
        var response = service.create(payload());
        var captor = org.mockito.ArgumentCaptor.forClass(PassbookEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTotalInterest()).isEqualByComparingTo("123.45");
        assertThat(captor.getValue().getFamilyId()).isEqualTo(31L);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(response.totalAmount()).isEqualByComparingTo("10123.45");
        assertThat(response.maturityDate()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(response.note()).isEqualTo("学费备用");
        assertThat(captor.getValue().getNote()).isEqualTo("学费备用");
        assertThat(response.bank()).isEqualTo("农商");
        assertThat(captor.getValue().getBank()).isEqualTo("农商");
        assertThat(response.interestRate()).isEqualByComparingTo("2.15");
    }

    @Test void rejectsMembersOutsideTheCurrentFamily() {
        var user = new UserEntity(); user.setStatus("ACTIVE");
        when(users.selectById(8L)).thenReturn(user);
        when(members.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(payload())).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(PassbookEntity.class));
    }

    @Test void memberCannotModifyAnotherCreatorsRecord() {
        var entity = new PassbookEntity(); entity.setFamilyId(31L); entity.setCreatedBy(8L);
        when(mapper.selectById(9L)).thenReturn(entity);
        assertThatThrownBy(() -> service.update(9L, payload())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).deleteById(9L);
        verify(mapper, never()).deleteById(any(PassbookEntity.class));
    }

    @Test void otherFamilyRecordsCannotBeModifiedEvenByAdmin() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.SUPER_ADMIN, 0));
        var entity = new PassbookEntity(); entity.setFamilyId(32L); entity.setCreatedBy(7L);
        when(mapper.selectById(9L)).thenReturn(entity);
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).deleteById(9L);
        verify(mapper, never()).deleteById(any(PassbookEntity.class));
    }

    @Test @SuppressWarnings("unchecked") void listScopesFamilyMemberAndInclusiveDateRange() {
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.list(8L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1, 20);
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("family_id =", "member_id =", "deposit_date >=", "deposit_date <=");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(31L).containsValue(8L);
    }

    @Test void returnsTotalsAcrossAllMatchingRecordsAlongsideTheRequestedPage() {
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<PassbookEntity> page = invocation.getArgument(0);
            page.setTotal(25);
            return page;
        });
        when(mapper.selectTotals(any())).thenReturn(new PassbookTotals(new BigDecimal("740000"), new BigDecimal("773235")));
        var response = service.list(8L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 2, 20);
        assertThat(response.totalPrincipal()).isEqualByComparingTo("740000");
        assertThat(response.totalAmount()).isEqualByComparingTo("773235");
        assertThat(response.total()).isEqualTo(25);
        assertThat(response.page()).isEqualTo(2);
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectTotals(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("family_id =", "deleted =", "member_id =", "deposit_date >=", "deposit_date <=").doesNotContain("ORDER BY", "LIMIT");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(31L).containsValue(8L).containsValue(LocalDate.of(2026, 1, 1));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(PassbookSort.class)
    @SuppressWarnings("unchecked")
    void sortsTheEntireResultBeforePagination(PassbookSort sort) {
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.list(null, null, null, 2, 20, sort);
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), captor.capture());
        var sql = captor.getValue().getSqlSegment();
        if (sort.name().startsWith("MATURITY")) {
            assertThat(sql).contains("ORDER BY DATE_ADD(deposit_date, INTERVAL term_years YEAR) " + (sort.name().endsWith("ASC") ? "ASC" : "DESC"));
        } else {
            assertThat(sql).contains("ORDER BY deposit_date " + (sort.name().endsWith("ASC") ? "ASC" : "DESC"));
        }
        assertThat(sql).contains("id DESC");
    }

    @Test void rejectsReversedDateRange() {
        assertThatThrownBy(() -> service.list(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 1, 20))
            .isInstanceOf(BusinessException.class);
        verify(mapper, never()).selectPage(any(), any());
    }

    @Test void validatesMoneyTermAndRate() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(payload())).isEmpty();
            var invalid = new PassbookPayload(8L, LocalDate.now(), new BigDecimal("-1"), 0, new BigDecimal("-1"), new BigDecimal("1.123"), "无效银行", "长".repeat(501));
            assertThat(validator.validate(invalid)).extracting(item -> item.getPropertyPath().toString())
                .contains("amount", "termYears", "interestRate", "totalInterest", "bank", "note");
        }
    }

    private void allowMember() {
        var user = new UserEntity(); user.setStatus("ACTIVE"); user.setDisplayName("妈妈");
        when(users.selectById(8L)).thenReturn(user);
        when(members.selectCount(any())).thenReturn(1L);
    }
    private PassbookPayload payload() {
        return new PassbookPayload(8L, LocalDate.of(2024, 2, 29), new BigDecimal("10000.00"), 3, new BigDecimal("2.15"), new BigDecimal("123.45"), "农商", "学费备用");
    }
}
