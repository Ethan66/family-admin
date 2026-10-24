package com.ethan.familyadmin.course;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class CourseAutoAttendanceJobTest {
    final CourseMapper courses = mock(CourseMapper.class);
    final CourseAutoAttendanceStateMapper states = mock(CourseAutoAttendanceStateMapper.class);
    final CourseAutoAttendanceService worker = mock(CourseAutoAttendanceService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-25T07:01:00Z"), ZoneId.of("Asia/Shanghai"));
    final CourseAutoAttendanceJob job = new CourseAutoAttendanceJob(courses, states, worker, clock);
    @Test void scheduleStartsAtNineAndRunsAtHalfHourBoundaries() throws Exception {
        var schedules=CourseAutoAttendanceJob.class.getMethod("run").getAnnotationsByType(org.springframework.scheduling.annotation.Scheduled.class);
        var expressions=new java.util.ArrayList<org.springframework.scheduling.support.CronExpression>();
        for(var schedule:schedules) {
            assertThat(schedule.zone()).isEqualTo("Asia/Shanghai");
            var expression=schedule.cron();
            expressions.add(org.springframework.scheduling.support.CronExpression.parse(expression.substring(expression.indexOf(':')+1,expression.length()-1)));
        }
        assertThat(next(expressions,LocalDateTime.of(2026,10,25,8,0))).isEqualTo(LocalDateTime.of(2026,10,25,9,0));
        assertThat(next(expressions,LocalDateTime.of(2026,10,25,9,0))).isEqualTo(LocalDateTime.of(2026,10,25,9,30));
        assertThat(next(expressions,LocalDateTime.of(2026,10,25,18,30))).isEqualTo(LocalDateTime.of(2026,10,25,19,0));
        assertThat(next(expressions,LocalDateTime.of(2026,10,25,19,0))).isEqualTo(LocalDateTime.of(2026,10,26,9,0));
    }
    private LocalDateTime next(List<org.springframework.scheduling.support.CronExpression> expressions,LocalDateTime time) {
        return expressions.stream().map(expression -> expression.next(time)).min(LocalDateTime::compareTo).orElseThrow();
    }
    @Test void restartsContinueFromPersistedProgressAndSkipNotYetFinishedCourses() {
        var enabled = LocalDateTime.of(2026, 10, 24, 22, 0);
        var now = LocalDateTime.of(2026, 10, 25, 15, 1);
        when(states.read()).thenReturn(new CourseAutoAttendanceStateMapper.State(enabled, enabled));
        var sunday = new CourseEntity(); sunday.setId(4L); sunday.setCourseDate(LocalDate.of(2000, 1, 9)); sunday.setEndTime(LocalTime.of(15, 0));
        var future = new CourseEntity(); future.setId(5L); future.setCourseDate(LocalDate.of(2000, 1, 9)); future.setEndTime(LocalTime.of(16, 0));
        when(courses.autoAttendanceCandidates()).thenReturn(List.of(sunday, future));
        job.run();
        verify(worker).complete(4L, LocalDate.of(2026, 10, 25), enabled, now);
        verify(worker, never()).complete(eq(5L), any(), any(), any());
        verify(states).advance(now);
    }
    @Test void aFailedCourseKeepsCheckpointForRetry() {
        var from = LocalDateTime.of(2026, 10, 25, 14, 0);
        when(states.read()).thenReturn(new CourseAutoAttendanceStateMapper.State(from, from));
        var course = new CourseEntity(); course.setId(4L); course.setCourseDate(LocalDate.of(2000, 1, 9)); course.setEndTime(LocalTime.of(15, 0));
        when(courses.autoAttendanceCandidates()).thenReturn(List.of(course));
        when(worker.complete(any(), any(), any(), any())).thenThrow(new IllegalStateException("test failure"));
        job.run();
        verify(states, never()).advance(any());
    }
}
