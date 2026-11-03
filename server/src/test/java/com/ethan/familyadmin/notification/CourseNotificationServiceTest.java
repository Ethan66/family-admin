package com.ethan.familyadmin.notification;

import com.ethan.familyadmin.course.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 提醒与扣课是两个独立结果，通知不触发任何扣课写入。 */
class CourseNotificationServiceTest {
    final CourseNotificationMapper mapper = mock(CourseNotificationMapper.class);
    final CourseNotificationProperties properties = properties();
    final Clock clock = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"), ZoneId.of("Asia/Shanghai"));
    final CourseNotificationService service = new CourseNotificationService(mapper, properties, clock);

    static CourseNotificationProperties properties() {
        var properties = new CourseNotificationProperties();
        properties.setEnabled(true);
        properties.setEnabledFrom(LocalDateTime.of(2026, 11, 3, 0, 0));
        var zc = new CourseNotificationProperties.Destination();
        zc.setKey("zc"); zc.setFamilyId(1L); zc.setUserId(2L);
        zc.setUrl("http://127.0.0.1:8644/webhooks/family-course"); zc.setSecret("a".repeat(32));
        var xi = new CourseNotificationProperties.Destination();
        xi.setKey("xi"); xi.setFamilyId(1L); xi.setUserId(3L);
        xi.setUrl("http://127.0.0.1:8645/webhooks/family-course"); xi.setSecret("b".repeat(32));
        properties.setDestinations(List.of(zc, xi));
        return properties;
    }

    @Test void upcomingAtThirtyMinutesEnqueuesOncePerRecipientAndSkipsLeaveAndOutsideWindow() {
        when(mapper.recipientAllowed(1L, 2L)).thenReturn(1);
        when(mapper.recipientAllowed(1L, 3L)).thenReturn(1);
        var course = course(1L, LocalTime.of(18, 0));
        var leave = course(2L, LocalTime.of(18, 0));
        var future = course(3L, LocalTime.of(18, 1));
        when(mapper.activeCourses(1L)).thenReturn(List.of(course, leave, future));
        var record = new CourseRecordEntity(); record.setStatus("LEAVE");
        when(mapper.record(2L, LocalDate.of(2026, 11, 3))).thenReturn(record);
        service.scanUpcoming();
        var tasks = org.mockito.ArgumentCaptor.forClass(CourseNotification.class);
        verify(mapper, times(2)).enqueue(tasks.capture());
        assertThat(tasks.getAllValues()).extracting(CourseNotification::getRecipientUserId).containsExactly(2L, 3L);
        assertThat(tasks.getAllValues()).allSatisfy(task -> {
            assertThat(task.getKind()).isEqualTo("UPCOMING");
            assertThat(task.getReferenceId()).isEqualTo(1L);
            assertThat(task.getContent()).contains("18:00", "han", "英语");
        });
    }

    @Test void attendanceUsesCommittedRecordAndIncludesEveryBalanceIncludingStandaloneSubject() {
        when(mapper.recipientAllowed(1L, 2L)).thenReturn(1);
        when(mapper.recipientAllowed(1L, 3L)).thenReturn(1);
        var record = new CourseRecordEntity(); record.setId(8L); record.setFamilyId(1L); record.setMemberName("han");
        record.setTitle("英语"); record.setStatus("ATTENDED"); record.setAutomatic(true);
        record.setRemainingLessons(new BigDecimal("2"));
        record.setCourseDate(LocalDate.of(2026, 11, 3)); record.setCreatedAt(LocalDateTime.of(2026, 11, 3, 17, 0));
        when(mapper.record(1L, record.getCourseDate())).thenReturn(record);
        when(mapper.familyBalances(1L)).thenReturn(List.of(line("英语", "2", "12"), line("画画", "14", "15"), line("篮球", "19", "19")));
        service.afterAutomaticAttendance(1L, record.getCourseDate());
        var tasks = org.mockito.ArgumentCaptor.forClass(CourseNotification.class);
        verify(mapper, times(2)).enqueue(tasks.capture());
        assertThat(tasks.getAllValues()).allSatisfy(task -> {
            assertThat(task.getKind()).isEqualTo("ATTENDANCE");
            assertThat(task.getReferenceId()).isEqualTo(8L);
            assertThat(task.getContent()).contains("扣减 1 课时", "英语：2/12", "画画：14/15", "篮球：19/19");
            assertThat(task.getContent()).doesNotContain("不足", "续课");
            assertThat(task.getDedupKey()).startsWith("ATTENDANCE:8:");
        });
    }

    @Test void disabledAndPreEnableOrManualRecordsNeverProduceMessages() {
        properties.setEnabled(false);
        service.scanUpcoming(); service.afterAutomaticAttendance(1L, LocalDate.now());
        verifyNoInteractions(mapper);
        properties.setEnabled(true);
        var record = new CourseRecordEntity(); record.setAutomatic(true); record.setStatus("ATTENDED");
        record.setCreatedAt(properties.getEnabledFrom().minusSeconds(1));
        when(mapper.record(1L, LocalDate.now())).thenReturn(record);
        service.afterAutomaticAttendance(1L, LocalDate.now());
        record.setCreatedAt(properties.getEnabledFrom()); record.setAutomatic(false);
        service.afterAutomaticAttendance(1L, LocalDate.now());
        verify(mapper, never()).enqueue(any());
    }

    @Test void disallowedRecipientsAreExcluded() {
        when(mapper.activeCourses(1L)).thenReturn(List.of(course(1L, LocalTime.of(18, 0))));
        when(mapper.recipientAllowed(1L, 2L)).thenReturn(1);
        service.scanUpcoming();
        var task = org.mockito.ArgumentCaptor.forClass(CourseNotification.class);
        verify(mapper).enqueue(task.capture());
        assertThat(task.getValue().getRecipientUserId()).isEqualTo(2L);
    }

    @Test void midnightWindowIncludesNextWeekdayButExcludesAlreadyStartedClass() {
        var lateClock = Clock.fixed(Instant.parse("2026-11-03T15:45:00Z"), ZoneId.of("Asia/Shanghai"));
        when(mapper.recipientAllowed(1L, 2L)).thenReturn(1);
        var nextDay = course(1L, LocalTime.of(0, 15));
        nextDay.setCourseDate(LocalDate.of(2000, 1, 5));
        var started = course(2L, LocalTime.of(23, 45));
        when(mapper.activeCourses(1L)).thenReturn(List.of(nextDay, started));
        new CourseNotificationService(mapper, properties, lateClock).scanUpcoming();
        var task = org.mockito.ArgumentCaptor.forClass(CourseNotification.class);
        verify(mapper).enqueue(task.capture());
        assertThat(task.getValue().getCourseDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(task.getValue().getReferenceId()).isEqualTo(1L);
    }

    private CourseEntity course(Long id, LocalTime start) {
        var course = new CourseEntity(); course.setId(id); course.setFamilyId(1L); course.setMemberId(4L);
        course.setMemberName("han"); course.setTitle("英语"); course.setCourseDate(LocalDate.of(2000, 1, 4));
        course.setStartTime(start); course.setEndTime(start.plusHours(1)); return course;
    }

    private CourseBalanceLine line(String title, String remaining, String total) {
        return new CourseBalanceLine(4L, "han", title, new BigDecimal(remaining), new BigDecimal(total));
    }
}
