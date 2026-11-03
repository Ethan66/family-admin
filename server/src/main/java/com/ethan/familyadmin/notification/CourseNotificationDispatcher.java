package com.ethan.familyadmin.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.Objects;

/** 网络发送与数据库事务分离，使用带版本条件的短租约恢复崩溃中的任务。 */
@Service
@ConditionalOnProperty(prefix="family-admin.course-notifications",name="enabled",havingValue="true")
public class CourseNotificationDispatcher {
    private static final Logger LOG=LoggerFactory.getLogger(CourseNotificationDispatcher.class);
    private final CourseNotificationMapper mapper;
    private final CourseNotificationProperties properties;
    private final HermesNotificationSender sender;
    private final Clock clock;

    public CourseNotificationDispatcher(CourseNotificationMapper mapper, CourseNotificationProperties properties,
                                         HermesNotificationSender sender, @Qualifier("courseAttendanceClock") Clock clock) {
        this.mapper=mapper; this.properties=properties; this.sender=sender; this.clock=clock;
    }

    /** 单轮限制发送数；一条任务故障不会阻断其他收件人的任务。 */
    public void dispatch() {
        var now=LocalDateTime.now(clock);
        for (var task:mapper.due(now,properties.getBatchSize())) {
            try { deliver(task); }
            catch (RuntimeException error) { LOG.warn("课程通知处理失败 task={} errorType={}",task.getId(),error.getClass().getSimpleName()); }
        }
    }

    private void deliver(CourseNotification task) {
        var now=LocalDateTime.now(clock);
        if (task.getAttempts()>=properties.getMaxAttempts()) {
            mapper.exhaust(task.getId(),task.getAttempts(),now); return;
        }
        if (mapper.claim(task.getId(),task.getAttempts(),now,now.plusMinutes(2))==0) return;
        task.setAttempts(task.getAttempts()+1);
        var target=properties.getDestinations().stream().filter(destination -> destination.getKey().equals(task.getDestinationKey())
            && destination.getFamilyId().equals(task.getFamilyId()) && destination.getUserId().equals(task.getRecipientUserId())).findFirst().orElse(null);
        if (target==null || mapper.recipientAllowed(task.getFamilyId(),task.getRecipientUserId())==0) {
            finish(task,"CANCELLED",now,"RECIPIENT_DISABLED"); return;
        }
        if (task.getCreatedAt().isBefore(properties.getEnabledFrom())) { finish(task,"CANCELLED",now,"PRE_ENABLE_TASK"); return; }
        if ("UPCOMING".equals(task.getKind())) {
            var course=mapper.activeCourse(task.getReferenceId());
            if (course==null || !Objects.equals(course.getFamilyId(),task.getFamilyId())
                || course.getCourseDate().getDayOfWeek()!=task.getCourseDate().getDayOfWeek()
                || mapper.record(course.getId(),task.getCourseDate())!=null) {
                finish(task,"CANCELLED",now,"COURSE_CANCELLED"); return;
            }
            var start=task.getCourseDate().atTime(course.getStartTime());
            if (!now.isBefore(start)) { finish(task,"CANCELLED",now,"COURSE_STARTED"); return; }
            var due=start.minusMinutes(properties.getLeadMinutes());
            if (now.isBefore(due)) {
                // 课程延后时只延后通知，不消耗发送次数，也不另造重复事件。
                mapper.defer(task.getId(),task.getAttempts(),due,now); return;
            }
            task.setContent(CourseNotificationService.upcomingContent(course,task.getCourseDate()));
        } else if ("ATTENDANCE".equals(task.getKind())) {
            var record=mapper.recordById(task.getReferenceId());
            if (record==null || !Objects.equals(record.getFamilyId(),task.getFamilyId()) || !Boolean.TRUE.equals(record.getAutomatic())
                || !"ATTENDED".equals(record.getStatus()) || record.getRemainingLessons()==null
                || record.getCreatedAt().isBefore(properties.getEnabledFrom())) {
                finish(task,"CANCELLED",now,"ATTENDANCE_CHANGED"); return;
            }
        } else { finish(task,"CANCELLED",now,"UNKNOWN_KIND"); return; }
        try {
            sender.send(target,task);
            finish(task,"SENT",LocalDateTime.now(clock),null);
        } catch (HermesNotificationSender.DeliveryException error) {
            var after=LocalDateTime.now(clock);
            var status=error.retryable() && task.getAttempts()<properties.getMaxAttempts() ? "PENDING" : "FAILED";
            var next=after.plusMinutes(1L << Math.min(3,task.getAttempts()-1));
            mapper.finish(task,status,next,after,error.getMessage());
            LOG.warn("课程通知发送失败 task={} status={} code={}",task.getId(),status,error.getMessage());
        }
    }

    private void finish(CourseNotification task,String status,LocalDateTime now,String error) {
        mapper.finish(task,status,now,now,error);
    }
}
