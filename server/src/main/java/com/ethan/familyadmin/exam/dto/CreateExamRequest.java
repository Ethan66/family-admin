package com.ethan.familyadmin.exam.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExamRequest(
    @NotBlank String name,
    @NotBlank String subject,
    @NotBlank String grade,
    @NotNull LocalDate examDate,
    @NotNull @DecimalMin("0.01") BigDecimal fullScore
) {
}
