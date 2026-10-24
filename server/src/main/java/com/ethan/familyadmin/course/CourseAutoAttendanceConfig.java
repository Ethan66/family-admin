package com.ethan.familyadmin.course;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;

@Configuration
@EnableScheduling
public class CourseAutoAttendanceConfig {
    @Bean
    public Clock courseAttendanceClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
}
