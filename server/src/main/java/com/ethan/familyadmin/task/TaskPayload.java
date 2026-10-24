package com.ethan.familyadmin.task;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskPayload(
    @NotBlank @Size(max = 120) String title,
    @NotNull @Positive Long memberId,
    @NotNull LocalDate taskDate,
    LocalTime taskTime,
    @NotNull @Size(max = 40) String category,
    @NotNull Boolean repeatDaily,
    @Size(max = 500) String note
) {}
