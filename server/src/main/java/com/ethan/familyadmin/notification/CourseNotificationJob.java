package com.ethan.familyadmin.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** 每分钟唤醒一次通知工作线程，前一批未结束时不重复排队。 */
@Component
@ConditionalOnProperty(prefix="family-admin.course-notifications",name="enabled",havingValue="true")
public class CourseNotificationJob {
    private static final Logger LOG=LoggerFactory.getLogger(CourseNotificationJob.class);
    private final CourseNotificationService service;
    private final CourseNotificationDispatcher dispatcher;
    private final TaskExecutor executor;
    private final AtomicBoolean running=new AtomicBoolean();

    public CourseNotificationJob(CourseNotificationService service, CourseNotificationDispatcher dispatcher,
                                  @Qualifier("courseNotificationExecutor") TaskExecutor executor) {
        this.service=service; this.dispatcher=dispatcher; this.executor=executor;
    }

    /** 任务不重叠，失败批次交由下个周期和持久化租约恢复。 */
    @Scheduled(fixedDelayString="${family-admin.course-notifications.scan-delay-ms:60000}",initialDelayString="15000")
    public void tick() {
        // 调度线程只投递一个工作批次；微信延迟不能拖住原来的自动记课任务。
        if (!running.compareAndSet(false,true)) return;
        try {
            executor.execute(() -> {
                try { service.scanUpcoming(); service.recoverCompleted(); dispatcher.dispatch(); }
                catch (RuntimeException error) { LOG.warn("课程通知批次失败 errorType={}",error.getClass().getSimpleName()); }
                finally { running.set(false); }
            });
        } catch (RuntimeException error) {
            running.set(false); LOG.warn("课程通知工作线程暂不可用 errorType={}",error.getClass().getSimpleName());
        }
    }
}
