package com.ethan.familyadmin.notification;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

/** 验证发送状态隔离、有限重试和收件人权限，失败不得再次触发扣课。 */
class CourseNotificationDispatcherTest {
    final CourseNotificationMapper mapper = mock(CourseNotificationMapper.class);
    final HermesNotificationSender sender = mock(HermesNotificationSender.class);
    final CourseNotificationProperties properties = CourseNotificationServiceTest.properties();
    final Clock clock = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"), ZoneId.of("Asia/Shanghai"));
    final CourseNotificationDispatcher dispatcher = new CourseNotificationDispatcher(mapper, properties, sender, clock);

    @Test void unauthorizedRecipientIsCancelledWithoutNetwork() {
        var task = task(); when(mapper.due(any(), anyInt())).thenReturn(List.of(task));
        when(mapper.claim(eq(1L), eq(0), any(), any())).thenReturn(1);
        dispatcher.dispatch();
        verify(mapper).finish(eq(task), eq("CANCELLED"), any(), any(), eq("RECIPIENT_DISABLED"));
        verifyNoInteractions(sender);
    }

    @Test void networkFailureRetriesWithSameDedupKeyAndEventuallyStops() {
        var task = task(); task.setKind("UPCOMING");
        var course = new com.ethan.familyadmin.course.CourseEntity();
        course.setId(1L); course.setFamilyId(1L); course.setCourseDate(LocalDate.of(2000,1,4));
        course.setStartTime(LocalTime.of(18,0)); course.setEndTime(LocalTime.of(19,0)); course.setTitle("英语"); course.setMemberName("han");
        when(mapper.activeCourse(1L)).thenReturn(course);
        when(mapper.recipientAllowed(1L, 2L)).thenReturn(1);
        when(mapper.due(any(), anyInt())).thenReturn(List.of(task));
        when(mapper.claim(eq(1L), anyInt(), any(), any())).thenReturn(1);
        doThrow(new HermesNotificationSender.DeliveryException("HTTP_502", true)).when(sender).send(any(), any());
        dispatcher.dispatch();
        verify(mapper).finish(eq(task), eq("PENDING"), eq(LocalDateTime.now(clock).plusMinutes(1)), any(), eq("HTTP_502"));
        assertThat(task.getDedupKey()).isEqualTo("UPCOMING:1:2026-11-03:2");
        task.setAttempts(4);
        dispatcher.dispatch();
        verify(mapper).finish(eq(task), eq("FAILED"), any(), any(), eq("HTTP_502"));
    }

    @Test void recordAlreadyAttendedOrOnLeaveCancelsUpcoming() {
        var task = task(); task.setKind("UPCOMING");
        var course = new com.ethan.familyadmin.course.CourseEntity(); course.setId(1L); course.setFamilyId(1L);
        course.setCourseDate(LocalDate.of(2000,1,4)); course.setStartTime(LocalTime.of(18,0));
        when(mapper.activeCourse(1L)).thenReturn(course);
        when(mapper.recipientAllowed(1L,2L)).thenReturn(1);
        when(mapper.due(any(),anyInt())).thenReturn(List.of(task));
        when(mapper.claim(eq(1L),eq(0),any(),any())).thenReturn(1);
        var record = new com.ethan.familyadmin.course.CourseRecordEntity(); record.setStatus("LEAVE");
        when(mapper.record(1L, task.getCourseDate())).thenReturn(record);
        dispatcher.dispatch();
        verify(mapper).finish(eq(task),eq("CANCELLED"),any(),any(),eq("COURSE_CANCELLED"));
        verifyNoInteractions(sender);
    }

    @Test void rescheduledClassDefersReminderWithoutUsingNetworkAttempt() {
        var task = task(); task.setKind("UPCOMING");
        var course = new com.ethan.familyadmin.course.CourseEntity();
        course.setFamilyId(1L); course.setCourseDate(LocalDate.of(2000,1,4));
        course.setStartTime(LocalTime.of(19,0));
        when(mapper.activeCourse(1L)).thenReturn(course);
        when(mapper.recipientAllowed(1L,2L)).thenReturn(1);
        when(mapper.due(any(),anyInt())).thenReturn(List.of(task));
        when(mapper.claim(eq(1L),eq(0),any(),any())).thenReturn(1);
        dispatcher.dispatch();
        verify(mapper).defer(1L,1,LocalDateTime.of(2026,11,3,18,30),LocalDateTime.now(clock));
        verify(mapper,never()).finish(any(),any(),any(),any(),any());
        verifyNoInteractions(sender);
    }

    private CourseNotification task() {
        var task = new CourseNotification(); task.setId(1L); task.setFamilyId(1L); task.setRecipientUserId(2L);
        task.setDestinationKey("zc"); task.setKind("ATTENDANCE"); task.setReferenceId(1L);
        task.setCourseDate(LocalDate.of(2026,11,3)); task.setCreatedAt(properties.getEnabledFrom());
        task.setDedupKey("UPCOMING:1:2026-11-03:2"); task.setTitle("提醒"); task.setContent("内容");
        return task;
    }
}
