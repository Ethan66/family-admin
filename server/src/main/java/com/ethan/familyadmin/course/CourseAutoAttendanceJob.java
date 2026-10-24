package com.ethan.familyadmin.course;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
@ConditionalOnProperty(prefix="family-admin.courses.auto-attendance",name="enabled",havingValue="true",matchIfMissing=true)
public class CourseAutoAttendanceJob {
    private static final Logger LOG=LoggerFactory.getLogger(CourseAutoAttendanceJob.class);
    private final CourseMapper courses;
    private final CourseAutoAttendanceStateMapper states;
    private final CourseAutoAttendanceService worker;
    private final Clock clock;
    public CourseAutoAttendanceJob(CourseMapper courses, CourseAutoAttendanceStateMapper states,
        CourseAutoAttendanceService worker, @Qualifier("courseAttendanceClock") Clock clock) {
        this.courses=courses; this.states=states; this.worker=worker; this.clock=clock;
    }

    /** 启用时间在启动完成时落库，不等到下一个半点，避免漏掉上线后到首次检查间结束的课。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        states.initialize(LocalDateTime.now(clock).withNano(0));
        var state=states.read();
        LOG.info("Automatic attendance enabledFrom={} scannedThrough={} schedule=09:00-19:00 every hour Asia/Shanghai",state.enabledFrom(),state.scannedThrough());
    }

    /** 检查点仅在整批成功后推进；失败重试及多实例扫描由课次行锁保证不重复扣减。 */
    @Scheduled(cron="${family-admin.courses.auto-attendance.cron:0 0 9-18 * * *}",zone="Asia/Shanghai")
    @Scheduled(cron="${family-admin.courses.auto-attendance.closing-cron:0 0 19 * * *}",zone="Asia/Shanghai")
    public void run() {
        var now=LocalDateTime.now(clock).withNano(0);
        int initialized=states.initialize(now);
        var state=states.read();
        var from=state.scannedThrough();
        if (now.isBefore(from)) return;
        var through=from.plusDays(7).isBefore(now) ? from.plusDays(7) : now;
        int completed=0;
        boolean failed=false;
        for (var course:courses.autoAttendanceCandidates()) {
            for (var date=from.toLocalDate(); !date.isAfter(through.toLocalDate()); date=date.plusDays(1)) {
                if (date.getDayOfWeek()!=course.getCourseDate().getDayOfWeek()) continue;
                var end=date.atTime(course.getEndTime());
                if (end.isBefore(from) || end.isAfter(through)) continue;
                try {
                    if (worker.complete(course.getId(),date,state.enabledFrom(),now)) completed++;
                } catch (RuntimeException error) {
                    failed=true;
                    LOG.error("Automatic attendance failed for family={} course={} date={}",course.getFamilyId(),course.getId(),date,error);
                }
            }
        }
        if (!failed) {
            states.advance(through);
            if (initialized==1 || completed>0) LOG.info("Automatic attendance scan enabledFrom={} through={} completed={}",state.enabledFrom(),through,completed);
        }
    }
}
