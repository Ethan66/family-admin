package com.ethan.familyadmin.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 不启动 Web 服务或连接数据库，只验证真实条件配置和 Bean 依赖装配。 */
class CourseNotificationContextTest {
    @Test void enabledConfigurationHasOneWorkerAndNoCircularDependency() {
        try(var context=new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("family-admin.course-notifications.enabled=true",
                "family-admin.course-notifications.enabled-from=2026-11-03T00:00:00",
                "family-admin.course-notifications.destinations[0].key=zc",
                "family-admin.course-notifications.destinations[0].family-id=1",
                "family-admin.course-notifications.destinations[0].user-id=2",
                "family-admin.course-notifications.destinations[0].url=http://127.0.0.1:8644/webhooks/family-course").applyTo(context);
            TestPropertyValues.of(java.util.Map.of(
                "family-admin.course-notifications.destinations[0].secret", java.util.UUID.randomUUID().toString())).applyTo(context);
            context.registerBean("courseAttendanceClock",Clock.class,() -> Clock.system(ZoneId.of("Asia/Shanghai")));
            context.registerBean(ObjectMapper.class,() -> new ObjectMapper());
            context.registerBean(CourseNotificationMapper.class,() -> mock(CourseNotificationMapper.class));
            context.register(CourseNotificationConfig.class,CourseNotificationService.class,HermesNotificationSender.class,
                CourseNotificationDispatcher.class,CourseNotificationJob.class);
            context.refresh();
            assertThat(context.getBean(CourseNotificationProperties.class).getLeadMinutes()).isEqualTo(30);
            assertThat(context.getBean("courseNotificationExecutor",org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class).getMaxPoolSize()).isEqualTo(1);
            assertThat(context.getBean(CourseNotificationJob.class)).isNotNull();
        }
    }

    @Test void disabledConfigurationDoesNotStartNetworkClientOrWorker() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(CourseNotificationConfig.class,HermesNotificationSender.class,CourseNotificationDispatcher.class,CourseNotificationJob.class);
            context.refresh();
            assertThat(context.getBean(CourseNotificationProperties.class).isEnabled()).isFalse();
            assertThat(context.getBeansOfType(HermesNotificationSender.class)).isEmpty();
            assertThat(context.containsBean("courseNotificationExecutor")).isFalse();
        }
    }
}
