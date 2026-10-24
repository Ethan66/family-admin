package com.ethan.familyadmin.course;
import java.time.LocalDate;
import java.time.LocalTime;
public record CourseOccurrence(Long id, Long createdBy, Long memberId, String memberName, String title,
    Integer dayOfWeek, LocalDate date, LocalTime startTime, LocalTime endTime,
    String color, String status, String note) {}
