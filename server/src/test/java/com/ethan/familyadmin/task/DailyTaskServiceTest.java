package com.ethan.familyadmin.task;

import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.family.FamilyMemberMapper;
import com.ethan.familyadmin.family.FamilyMemberEntity;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.family.UserEntity;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class DailyTaskServiceTest {
    private final DailyTaskMapper mapper = mock(DailyTaskMapper.class);
    private final TaskCompletionMapper completions = mock(TaskCompletionMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final FamilyMemberMapper members = mock(FamilyMemberMapper.class);
    private final DailyTaskService service = new DailyTaskService(mapper, completions, users, members);
    @BeforeEach void setup() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), DailyTaskEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), FamilyMemberEntity.class);
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    private DailyTaskEntity task() {
        var task = new DailyTaskEntity();
        task.setId(1L); task.setFamilyId(31L); task.setMemberId(7L);
        task.setMemberName("爸爸"); task.setTitle("阅读"); task.setRepeatDaily(true);
        task.setTaskDate(LocalDate.of(2026, 10, 22));
        return task;
    }
    @Test void repeatedTaskHasIndependentDailyCompletion() {
        when(mapper.selectList(any())).thenReturn(List.of(task()));
        when(completions.list(any(), any(), any())).thenReturn(List.of(new TaskCompletionMapper.Completion(1L, LocalDate.of(2026, 10, 23))));
        var result = service.list(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 24), null);
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(TaskOccurrence::completed)).containsExactly(false, true, false);
    }
    @Test void memberCanListWholeFamilyWithoutImplicitSelfFilter() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("family_id =").doesNotContain("member_id =");
            assertThat(query.getParamNameValuePairs().values()).contains(31L);
            return List.of(task());
        });
        when(completions.list(any(), any(), any())).thenReturn(List.of());
        service.list(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 24), null);
        verify(completions).list(31L, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 24));
    }
    @Test void memberCanFilterAnotherMemberWithinCurrentFamily() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("family_id =", "member_id =");
            assertThat(query.getParamNameValuePairs().values()).contains(31L, 8L);
            return List.of();
        });
        when(completions.list(any(), any(), any())).thenReturn(List.of());
        service.list(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 24), 8L);
    }
    @Test void memberCanCreateTaskForAnotherActiveFamilyMember() {
        var user = new UserEntity(); user.setId(8L); user.setDisplayName("妈妈"); user.setStatus("ACTIVE");
        when(users.selectById(8L)).thenReturn(user);
        when(members.selectCount(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("family_id =", "user_id =");
            assertThat(query.getParamNameValuePairs().values()).contains(31L, 8L);
            return 1L;
        });
        service.create(new TaskPayload("阅读", 8L, LocalDate.of(2026, 10, 24), null, "", false, null));
        var saved = org.mockito.ArgumentCaptor.forClass(DailyTaskEntity.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(31L);
        assertThat(saved.getValue().getMemberId()).isEqualTo(8L);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
    }
    @Test void rejectsTaskAssignedToSomeoneOutsideTheFamily() {
        var user = new UserEntity(); user.setId(8L); user.setDisplayName("其他家庭成员"); user.setStatus("ACTIVE");
        when(users.selectById(8L)).thenReturn(user);
        when(members.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(new TaskPayload("阅读", 8L, LocalDate.of(2026, 10, 24), null, "", false, null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(mapper, never()).insert(any(DailyTaskEntity.class));
    }
    @Test void administratorMustAlsoSelectAMemberOfTheCurrentFamily() {
        TenantContext.set(new CurrentUser(1L, 31L, Role.SUPER_ADMIN, 0));
        var user = new UserEntity(); user.setId(1L); user.setDisplayName("管理员"); user.setStatus("ACTIVE");
        when(users.selectById(1L)).thenReturn(user);
        when(members.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(new TaskPayload("阅读", 1L, LocalDate.of(2026, 10, 24), null, "", false, null)))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(mapper, never()).insert(any(DailyTaskEntity.class));
    }
    @Test void anotherFamilyCannotReadOrCompleteTask() {
        var task = task(); task.setFamilyId(99L);
        when(mapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> service.complete(1L, LocalDate.of(2026, 10, 23), true)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(completions);
    }
    @Test void memberCannotCompleteAnotherMembersTask() {
        var task = task(); task.setMemberId(8L);
        when(mapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> service.complete(1L, LocalDate.of(2026, 10, 23), true)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(completions);
    }
    @Test void cannotCompleteOccurrenceBeforeStartOrOutsideSingleTaskDate() {
        var task = task(); task.setRepeatDaily(false);
        when(mapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> service.complete(1L, LocalDate.of(2026, 10, 21), true)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.complete(1L, LocalDate.of(2026, 10, 23), true)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(completions);
    }
    @Test void requiresFamilyAndBoundsCalendarRange() {
        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null)).isInstanceOf(BusinessException.class);
        TenantContext.set(new CurrentUser(1L, null, Role.SUPER_ADMIN, 0));
        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper, completions);
    }
    @Test void softDeleteCarriesCurrentActorAndTimestamp() {
        var task = task(); task.setUpdatedBy(99L);
        when(mapper.selectById(1L)).thenReturn(task);
        service.delete(1L);
        verify(mapper).deleteById(task);
        assertThat(task.getUpdatedBy()).isEqualTo(7L);
        assertThat(task.getUpdatedAt()).isNotNull();
    }
}
