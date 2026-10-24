package com.ethan.familyadmin.course;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
public record CoursePayload(
    @NotBlank @Size(max = 120) String title,
    @NotNull @Positive Long memberId,
    @NotNull @Min(1) @Max(7) Integer dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @Pattern(regexp = "blue|purple|green|cyan|orange") String color
) {}
