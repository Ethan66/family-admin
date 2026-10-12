package com.ethan.familyadmin.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePhotoRequest(
    @NotBlank String title,
    @NotBlank String album,
    @NotNull LocalDate photoDate,
    String description,
    @NotNull Long fileId
) {
}
