package com.ethan.familyadmin.task;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskOccurrence(Long id, String title, Long memberId, String memberName,
    LocalDate taskDate, LocalDate startDate, LocalTime taskTime, String category,
    boolean repeatDaily, boolean completed, String note) {}
