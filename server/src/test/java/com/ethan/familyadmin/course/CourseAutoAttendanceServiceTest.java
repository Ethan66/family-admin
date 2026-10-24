package com.ethan.familyadmin.course;

import org.junit.jupiter.api.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CourseAutoAttendanceServiceTest {
    final CourseMapper courses = mock(CourseMapper.class);
    final CourseRecordMapper records = mock(CourseRecordMapper.class);
    final CourseBalanceMapper balances = mock(CourseBalanceMapper.class);
    final CourseAutoAttendanceService service = new CourseAutoAttendanceService(courses, records, balances);
    final LocalDate date = LocalDate.of(2026, 10, 25);
    final LocalDateTime enabled = LocalDateTime.of(2026, 10, 24, 22, 0);
    CourseEntity course;
    @BeforeEach void setup() {
        course = new CourseEntity(); course.setId(4L); course.setFamilyId(1L); course.setMemberId(4L);
        course.setMemberName("han"); course.setCreatedBy(2L); course.setTitle("英语");
        course.setCourseDate(LocalDate.of(2000, 1, 9)); course.setStartTime(LocalTime.of(13, 30)); course.setEndTime(LocalTime.of(15, 0));
        course.setCreatedAt(enabled.minusDays(1)); course.setUpdatedAt(enabled.minusDays(1));
        when(courses.selectForUpdate(4L)).thenReturn(course);
        when(courses.autoAttendanceEligible(4L, 1L, 4L)).thenReturn(1);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), CourseRecordEntity.class);
    }
    @Test void beforeEndTimeDoesNothing() {
        assertThat(service.complete(4L, date, enabled, date.atTime(14, 59))).isFalse();
        verify(records, never()).upsert(any()); verifyNoInteractions(balances);
    }
    @Test void afterEndTimeWritesAutomaticRecordAndDeductsOne() {
        assertThat(service.complete(4L, date, enabled, date.atTime(15, 0))).isTrue();
        var record = org.mockito.ArgumentCaptor.forClass(CourseRecordEntity.class);
        verify(records).upsert(record.capture());
        assertThat(record.getValue().getAutomatic()).isTrue();
        assertThat(record.getValue().getFamilyId()).isEqualTo(1L);
        assertThat(record.getValue().getCourseDate()).isEqualTo(date);
        assertThat(record.getValue().getStatus()).isEqualTo("ATTENDED");
        verify(balances).adjust(1L, 4L, "英语", -1, 2L);
    }
    @Test void bothManualAttendanceAndLeaveAreSkipped() {
        for (var status : new String[]{"ATTENDED", "LEAVE"}) {
            var existing = new CourseRecordEntity(); existing.setStatus(status);
            when(records.selectOne(any())).thenReturn(existing);
            assertThat(service.complete(4L, date, enabled, date.atTime(16, 0))).isFalse();
        }
        verify(records, never()).upsert(any()); verifyNoInteractions(balances);
    }
    @Test void disabledFamilyOrMemberDoesNotProduceRecords() {
        when(courses.autoAttendanceEligible(4L, 1L, 4L)).thenReturn(0);
        assertThat(service.complete(4L, date, enabled, date.atTime(16, 0))).isFalse();
        verify(records, never()).upsert(any());
    }
    @Test void historyBeforeActivationAndBeforeCourseChangesIsSkipped() {
        assertThat(service.complete(4L, date, date.atTime(15, 1), date.atTime(16, 0))).isFalse();
        course.setUpdatedAt(date.atTime(15, 1));
        assertThat(service.complete(4L, date, enabled, date.atTime(16, 0))).isFalse();
    }
}
