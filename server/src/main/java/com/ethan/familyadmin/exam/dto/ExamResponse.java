package com.ethan.familyadmin.exam.dto;

import com.ethan.familyadmin.exam.ExamStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExamResponse(
    Long id,
    String name,
    String subject,
    String grade,
    LocalDate examDate,
    BigDecimal fullScore,
    BigDecimal score,
    ExamStatus status,
    String reviewNote,
    Long createdBy,
    LocalDateTime createdAt
) {
}
