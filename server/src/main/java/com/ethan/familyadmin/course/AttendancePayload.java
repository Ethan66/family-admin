package com.ethan.familyadmin.course;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record AttendancePayload(
    @NotNull LocalDate date,
    @NotNull @Pattern(regexp = "ATTENDED|LEAVE") String status,
    @NotNull @Size(max = 500) String note
) {}
