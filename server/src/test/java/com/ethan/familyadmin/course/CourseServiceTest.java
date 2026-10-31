package com.ethan.familyadmin.course;

import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import com.ethan.familyadmin.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CourseServiceTest {
    final CourseMapper mapper = mock(CourseMapper.class);
    final CourseRecordMapper records = mock(CourseRecordMapper.class);
    final UserMapper users = mock(UserMapper.class);
    final FamilyMemberMapper members = mock(FamilyMemberMapper.class);
    final CourseBalanceMapper balances = mock(CourseBalanceMapper.class);
    final CourseService service = new CourseService(mapper, records, users, members, balances);
    @BeforeEach void setup() {
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        when(mapper.selectForUpdate(any())).thenAnswer(invocation -> mapper.selectById((Long) invocation.getArgument(0)));
        for (var type : List.of(CourseEntity.class, CourseRecordEntity.class, CourseBalanceEntity.class, FamilyMemberEntity.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), type);
        }
    }
    @AfterEach void cleanup() { TenantContext.clear(); }
    CourseEntity course() {
        var course = new CourseEntity();
        course.setId(1L); course.setFamilyId(31L); course.setMemberId(7L); course.setMemberName("小宇");
        course.setTitle("英语"); course.setCourseDate(LocalDate.of(2026, 10, 19)); course.setRepeatWeekly(true);
        course.setStartTime(LocalTime.of(15, 0)); course.setEndTime(LocalTime.of(16, 30)); course.setColor("blue");
        return course;
    }
    @Test void listsFixedWeekdaysRegardlessOfOriginalStartDate() {
        var course = course(); course.setCourseDate(LocalDate.of(2099, 1, 5)); course.setRepeatWeekly(false);
        when(mapper.selectList(any())).thenReturn(List.of(course));
        when(records.selectList(any())).thenReturn(List.of());
        var result = service.list(null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().dayOfWeek()).isEqualTo(course.getCourseDate().getDayOfWeek().getValue());
        assertThat(result.getFirst().date().getDayOfWeek()).isEqualTo(course.getCourseDate().getDayOfWeek());
        assertThat(result).extracting(CourseOccurrence::status).containsOnly("PENDING");
    }
    @Test void historyAppliesFiltersAndPreservesStoredSnapshotsAndUnknownLegacyBalances() {
        var row = new CourseRecordEntity(); row.setMemberId(7L); row.setTitle("英语");
        row.setRemainingLessons(new java.math.BigDecimal("11")); row.setTotalLessons(new java.math.BigDecimal("12"));
        var legacy = new CourseRecordEntity(); legacy.setMemberId(7L); legacy.setTitle("英语");
        var balance = new CourseBalanceEntity(); balance.setMemberId(7L); balance.setTitle("英语");
        balance.setRemainingLessons(new java.math.BigDecimal("10")); balance.setTotalLessons(new java.math.BigDecimal("40"));
        lenient().when(balances.selectList(any())).thenReturn(List.of(balance));
        when(records.selectPage(any(), any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(1);
            assertThat(query.getSqlSegment()).contains("family_id =", "title =", "status =", "course_date >=", "course_date <");
            assertThat(query.getParamNameValuePairs().values()).contains(31L, "英语", "ATTENDED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
            var page = (com.baomidou.mybatisplus.extension.plugins.pagination.Page<CourseRecordEntity>) invocation.getArgument(0);
            return page.setRecords(List.of(row, legacy)).setTotal(2);
        });
        var result = service.history(null, 1, "英语", "ATTENDED", "2026-10");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records().getFirst().getRemainingLessons()).isEqualByComparingTo("11");
        assertThat(result.records().getFirst().getTotalLessons()).isEqualByComparingTo("12");
        assertThat(result.records().getLast().getRemainingLessons()).isNull();
        assertThat(result.records().getLast().getTotalLessons()).isNull();
        verify(balances, never()).selectList(any());
    }

    @Test void newAttendanceStoresTheBalanceAfterDeductionUnderSubjectLock() {
        var course = course(); course.setCourseDate(LocalDate.now());
        when(mapper.selectById(1L)).thenReturn(course);
        var balance = new CourseBalanceEntity(); balance.setRemainingLessons(new java.math.BigDecimal("12"));
        balance.setTotalLessons(new java.math.BigDecimal("12"));
        when(balances.adjust(31L, 7L, "英语", -1, 7L)).thenAnswer(invocation -> {
            balance.setRemainingLessons(new java.math.BigDecimal("11")); return 1;
        });
        when(balances.selectOne(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("family_id =", "member_id =", "title =", "FOR UPDATE");
            return balance;
        });
        service.record(1L, new AttendancePayload(LocalDate.now(), "ATTENDED", "首次记录"));
        var record = org.mockito.ArgumentCaptor.forClass(CourseRecordEntity.class);
        verify(records).upsert(record.capture());
        assertThat(record.getValue().getRemainingLessons()).isEqualByComparingTo("11");
        assertThat(record.getValue().getTotalLessons()).isEqualByComparingTo("12");
        var order = inOrder(balances, records);
        order.verify(balances).adjust(31L, 7L, "英语", -1, 7L);
        order.verify(balances).selectOne(any());
        order.verify(records).upsert(any());
    }

    @Test void noteOnlyEditsKeepTheOriginalSnapshotAfterBalanceRecalibration() {
        var course = course(); course.setCourseDate(LocalDate.now());
        when(mapper.selectById(1L)).thenReturn(course);
        var saved = new CourseRecordEntity(); saved.setMemberId(7L); saved.setTitle("英语"); saved.setStatus("ATTENDED");
        saved.setRemainingLessons(new java.math.BigDecimal("11")); saved.setTotalLessons(new java.math.BigDecimal("12"));
        when(records.selectOne(any())).thenReturn(saved);
        service.record(1L, new AttendancePayload(LocalDate.now(), "ATTENDED", "只更新备注"));
        var record = org.mockito.ArgumentCaptor.forClass(CourseRecordEntity.class);
        verify(records).upsert(record.capture());
        assertThat(record.getValue().getRemainingLessons()).isEqualByComparingTo("11");
        assertThat(record.getValue().getTotalLessons()).isEqualByComparingTo("12");
        verifyNoInteractions(balances);
    }

    @Test void leaveRefundStoresNewSnapshotForTheOriginalSubjectAfterCourseRename() {
        var course = course(); course.setCourseDate(LocalDate.now()); course.setTitle("英语新版");
        when(mapper.selectById(1L)).thenReturn(course);
        var saved = new CourseRecordEntity(); saved.setMemberId(7L); saved.setTitle("英语"); saved.setStatus("ATTENDED");
        when(records.selectOne(any())).thenReturn(saved);
        var balance = new CourseBalanceEntity(); balance.setRemainingLessons(new java.math.BigDecimal("21"));
        balance.setTotalLessons(new java.math.BigDecimal("24"));
        when(balances.selectOne(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            query.getSqlSegment();
            assertThat(query.getParamNameValuePairs()).containsValue("英语").doesNotContainValue("英语新版");
            return balance;
        });
        service.record(1L, new AttendancePayload(LocalDate.now(), "LEAVE", "请假退回"));
        var record = org.mockito.ArgumentCaptor.forClass(CourseRecordEntity.class);
        verify(records).upsert(record.capture());
        verify(balances).adjust(31L, 7L, "英语", 1, 7L);
        assertThat(record.getValue().getRemainingLessons()).isEqualByComparingTo("21");
        assertThat(record.getValue().getTotalLessons()).isEqualByComparingTo("24");
    }
    @Test void rejectsInvalidHistoryMonthAndStatus() {
        assertThatThrownBy(() -> service.history(null, 1, null, null, "2026-13")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.history(null, 1, null, "PENDING", null)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsOtherFamilyAndOtherMemberWrites() {
        var course = course(); course.setFamilyId(99L);
        when(mapper.selectById(1L)).thenReturn(course);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        course.setFamilyId(31L); course.setMemberId(8L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).deleteById(any(CourseEntity.class));
    }
    @Test void creatorCanEditCourseAssignedToAnotherMember() {
        var course = course(); course.setMemberId(8L); course.setCreatedBy(7L);
        when(mapper.selectById(1L)).thenReturn(course);
        var user = new UserEntity(); user.setDisplayName("小宝"); user.setStatus("ACTIVE");
        when(users.selectById(8L)).thenReturn(user); when(members.selectCount(any())).thenReturn(1L);
        service.update(1L, new CoursePayload("英语口语", 8L, 1, course.getStartTime(), course.getEndTime(), "blue"));
        verify(mapper).updateById(course);
        assertThat(course.getCreatedBy()).isEqualTo(7L);
        assertThat(course.getTitle()).isEqualTo("英语口语");
    }
    @Test void creatorStillCannotEditAcrossFamilies() {
        var course = course(); course.setCreatedBy(7L); course.setFamilyId(99L);
        when(mapper.selectById(1L)).thenReturn(course);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsReversedTimeAndNonFamilyMember() {
        assertThatThrownBy(() -> service.create(new CoursePayload("英语", 7L, 1, LocalTime.NOON, LocalTime.NOON, "blue")))
            .isInstanceOf(BusinessException.class);
        var user = new UserEntity(); user.setStatus("ACTIVE");
        when(users.selectById(8L)).thenReturn(user); when(members.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(new CoursePayload("英语", 8L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "blue")))
            .isInstanceOf(BusinessException.class);
    }
    @Test void validatesWeekdayRange() {
        for (int day : List.of(0, 8)) {
            assertThatThrownBy(() -> service.create(new CoursePayload("英语", 7L, day, LocalTime.of(9, 0), LocalTime.of(10, 0), "blue")))
                .isInstanceOf(BusinessException.class);
        }
    }
    @Test void rejectsNonOccurrenceAndFutureAttendance() {
        when(mapper.selectById(1L)).thenReturn(course());
        assertThatThrownBy(() -> service.record(1L, new AttendancePayload(LocalDate.of(2026, 10, 20), "ATTENDED", "")))
            .isInstanceOf(BusinessException.class);
        var course = course(); course.setCourseDate(LocalDate.now().plusDays(1));
        when(mapper.selectById(1L)).thenReturn(course);
        assertThatThrownBy(() -> service.record(1L, new AttendancePayload(course.getCourseDate(), "ATTENDED", "")))
            .isInstanceOf(BusinessException.class);
    }
    @Test void canMarkFutureLeaveBeforeAutomaticAttendanceRuns() {
        var course=course(); course.setCourseDate(LocalDate.now().plusDays(1));
        when(mapper.selectById(1L)).thenReturn(course);
        service.record(1L,new AttendancePayload(course.getCourseDate(),"LEAVE","提前请假"));
        verify(records).upsert(any());
        verify(balances,never()).adjust(any(),any(),any(),anyInt(),any());
    }
    @Test void recordUpsertUsesAuthenticatedFamilyAndCourseSnapshot() {
        var course = course(); course.setCourseDate(LocalDate.now().minusDays(7));
        when(mapper.selectById(1L)).thenReturn(course);
        service.record(1L, new AttendancePayload(course.getCourseDate(), "ATTENDED", "已完成练习"));
        var captor = org.mockito.ArgumentCaptor.forClass(CourseRecordEntity.class);
        verify(records).upsert(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(31L);
        assertThat(captor.getValue().getTitle()).isEqualTo("英语");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        verify(balances).adjust(31L, 7L, "英语", -1, 7L);
    }
    @Test void resavingAttendanceDoesNotDeductTwiceAndLeaveRefundsOneLesson() {
        var course = course(); course.setCourseDate(LocalDate.now());
        var saved = new CourseRecordEntity(); saved.setMemberId(7L); saved.setTitle("英语"); saved.setStatus("ATTENDED");
        when(mapper.selectById(1L)).thenReturn(course); when(records.selectOne(any())).thenReturn(saved);
        service.record(1L, new AttendancePayload(LocalDate.now(), "ATTENDED", "补充备注"));
        verify(balances, never()).adjust(any(), any(), any(), anyInt(), any());
        service.record(1L, new AttendancePayload(LocalDate.now(), "LEAVE", "请假"));
        verify(balances).adjust(31L, 7L, "英语", 1, 7L);
    }
    @Test void sameSubjectAcrossWeekdaysSharesOneBalance() {
        var monday = course(); var sunday = course(); sunday.setId(2L); sunday.setCourseDate(LocalDate.of(2000, 1, 9));
        when(mapper.selectList(any())).thenReturn(List.of(monday, sunday));
        when(balances.selectList(any())).thenReturn(List.of());
        assertThat(service.balances(null)).hasSize(1);
        assertThat(service.balances(null).getFirst().remainingLessons()).isNull();
    }
    @Test void standaloneBasketballBalanceIsVisibleWithoutAddingAScheduledCourse() {
        var balance = new CourseBalanceEntity(); balance.setMemberId(7L); balance.setCreatedBy(7L);
        balance.setTitle("篮球"); balance.setRemainingLessons(new java.math.BigDecimal("19"));
        var user = new UserEntity(); user.setDisplayName("小宇");
        when(users.selectById(7L)).thenReturn(user);
        when(mapper.selectList(any())).thenReturn(List.of()); when(balances.selectList(any())).thenReturn(List.of(balance));
        var result = service.balances(null);
        assertThat(result).hasSize(1); assertThat(result.getFirst().title()).isEqualTo("篮球");
        assertThat(result.getFirst().remainingLessons()).isEqualByComparingTo("19");
    }
    @Test void creatorCanRecordForChildAndBalanceIsDeductedOnce() {
        var course = course(); course.setCreatedBy(7L); course.setMemberId(8L); course.setCourseDate(LocalDate.now());
        when(mapper.selectById(1L)).thenReturn(course);
        service.record(1L, new AttendancePayload(LocalDate.now(), "ATTENDED", ""));
        verify(balances).adjust(31L, 8L, "英语", -1, 7L);
    }
    @Test void reassignedCourseDoesNotLetNewMemberOverwriteOriginalMembersHistory() {
        var course = course(); course.setCourseDate(LocalDate.now());
        var saved = new CourseRecordEntity(); saved.setMemberId(8L);
        when(mapper.selectById(1L)).thenReturn(course);
        when(records.selectOne(any())).thenReturn(saved);
        assertThatThrownBy(() -> service.record(1L, new AttendancePayload(LocalDate.now(), "ATTENDED", "改写")))
            .isInstanceOf(BusinessException.class);
        verify(records, never()).upsert(any());
    }
}
