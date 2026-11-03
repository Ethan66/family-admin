package com.ethan.familyadmin.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 通知复用当前 JVM，只启用一个有界工作线程，不占用现有自动记课调度线程。 */
@Configuration
@EnableConfigurationProperties(CourseNotificationProperties.class)
public class CourseNotificationConfig {
    /** 不积压额外批次，进程退出时有界等待正在发送的通知。 */
    @Bean("courseNotificationExecutor")
    @ConditionalOnProperty(prefix="family-admin.course-notifications",name="enabled",havingValue="true")
    public ThreadPoolTaskExecutor courseNotificationExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("course-notify-"); executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15); return executor;
    }
}
