package com.ethan.familyadmin.passbook;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 覆盖账号白名单、分页统计与写接口，避免隐藏列表后仍可旁路访问。 */
@ExtendWith(MockitoExtension.class)
class PassbookPrivacyTest {
    @Mock PassbookMapper mapper;
    @Mock UserMapper users;
    @Mock FamilyMemberMapper members;
    PassbookService service;

    @BeforeEach void setUp() {
        service = new PassbookService(mapper, users, members, new TenantAccessGuard());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "privacy"), PassbookEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "membership"), FamilyMemberEntity.class);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    @ParameterizedTest
    @CsvSource(value = {"xi,FAMILY_MEMBER,true,8", "other_admin,SUPER_ADMIN,true,8", "zc,FAMILY_MEMBER,false,8", "admin,SUPER_ADMIN,false,8",
        "xi,FAMILY_MEMBER,true,NULL", "other_admin,SUPER_ADMIN,true,NULL", "zc,FAMILY_MEMBER,false,NULL", "admin,SUPER_ADMIN,false,NULL"}, nullValues = "NULL")
    @SuppressWarnings("unchecked")
    void listAndTotalsApplyTheSameAccountVisibility(String username, Role role, boolean restricted, Long memberId) {
        actor(username, role);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.selectTotals(any())).thenReturn(new PassbookTotals(BigDecimal.ZERO, BigDecimal.ZERO));

        // 显式筛选 zc 时也必须追加限制，不能让请求参数覆盖权限条件。
        service.list(memberId, null, null, 1, 20);

        var pages = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        var totals = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), pages.capture());
        verify(mapper).selectTotals(totals.capture());
        for (var query : java.util.List.of(pages.getValue(), totals.getValue())) {
            assertThat(query.getSqlSegment()).contains("family_id =");
            assertThat(query.getParamNameValuePairs()).containsValue(31L);
            if (memberId != null) {
                assertThat(query.getSqlSegment()).contains("member_id =");
                assertThat(query.getParamNameValuePairs()).containsValue(memberId);
            }
            if (restricted) {
                assertThat(query.getSqlSegment()).contains("member_id NOT IN", "SELECT id FROM sys_user WHERE username = 'zc'");
            } else {
                assertThat(query.getSqlSegment()).doesNotContain("NOT IN");
            }
        }
    }

    @Test void otherMemberCannotCreateForZcOrMoveOwnRecordToZc() {
        actor("xi", Role.FAMILY_MEMBER);
        owner(8L, "zc");
        lenient().when(members.selectCount(any())).thenReturn(1L);
        owner(9L, "xi");
        when(mapper.selectById(5L)).thenReturn(record(9L));

        assertThatThrownBy(() -> service.create(payload(8L))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(5L, payload(8L))).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(PassbookEntity.class));
        verify(mapper, never()).updateById(any(PassbookEntity.class));
    }

    @ParameterizedTest
    @CsvSource({"xi,FAMILY_MEMBER", "other_admin,SUPER_ADMIN"})
    void creatorCannotReadThroughUpdateOrDeleteEvenIfTheyCreatedZcRecord(String username, Role role) {
        actor(username, role);
        owner(8L, "zc");
        owner(9L, "xi");
        when(mapper.selectById(5L)).thenReturn(record(8L));
        lenient().when(members.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(5L, payload(8L))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(5L, payload(9L))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateById(any(PassbookEntity.class));
        verify(mapper, never()).deleteById(any(PassbookEntity.class));
    }

    @ParameterizedTest
    @CsvSource({"zc,FAMILY_MEMBER", "admin,SUPER_ADMIN"})
    void allowedAccountsRetainExistingCreateUpdateDeletePermissions(String username, Role role) {
        actor(username, role);
        owner(8L, "zc");
        when(members.selectCount(any())).thenReturn(1L);
        var entity = record(8L);
        entity.setCreatedBy(TenantContext.requiredUser().userId());
        when(mapper.selectById(5L)).thenReturn(entity);

        assertThat(service.create(payload(8L)).memberId()).isEqualTo(8L);
        assertThat(service.update(5L, payload(8L)).memberId()).isEqualTo(8L);
        service.delete(5L);
        verify(mapper).deleteById(any(PassbookEntity.class));
    }

    @Test void ordinaryMemberRecordsKeepTheirExistingBehavior() {
        actor("xi", Role.FAMILY_MEMBER);
        owner(9L, "another_member");
        when(members.selectCount(any())).thenReturn(1L);
        when(mapper.selectById(5L)).thenReturn(record(9L));
        assertThat(service.create(payload(9L)).memberId()).isEqualTo(9L);
        assertThat(service.update(5L, payload(9L)).memberId()).isEqualTo(9L);
        service.delete(5L);
        verify(mapper).deleteById(any(PassbookEntity.class));
    }

    private void actor(String username, Role role) {
        Long id = "zc".equals(username) ? 8L : 7L;
        TenantContext.set(new CurrentUser(id, 31L, role, 0));
        var user = user(id, username);
        lenient().when(users.selectById(id)).thenReturn(user);
    }

    private void owner(Long id, String username) {
        lenient().when(users.selectById(id)).thenReturn(user(id, username));
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setStatus("ACTIVE");
        user.setDisplayName(username);
        return user;
    }

    private PassbookEntity record(Long memberId) {
        var entity = new PassbookEntity();
        entity.setId(5L);
        entity.setFamilyId(31L);
        entity.setCreatedBy(7L);
        entity.setMemberId(memberId);
        return entity;
    }

    private PassbookPayload payload(Long memberId) {
        return new PassbookPayload(memberId, LocalDate.of(2024, 1, 1), new BigDecimal("100"), 1,
            BigDecimal.ONE, BigDecimal.ONE, "农商", null);
    }
}
