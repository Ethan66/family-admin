package com.ethan.familyadmin.exam.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReviewExamRequest(
    @NotNull @DecimalMin("0") BigDecimal score,
    String reviewNote
) {
}
