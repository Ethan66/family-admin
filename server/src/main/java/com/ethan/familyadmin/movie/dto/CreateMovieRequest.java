package com.ethan.familyadmin.movie.dto;

import com.ethan.familyadmin.movie.WatchStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateMovieRequest(
    @NotBlank String title, @NotBlank String type, @NotNull @Min(1888) Integer releaseYear,
    String director, @NotNull @Positive Integer minutes,
    @DecimalMin("0") @DecimalMax("10") BigDecimal rating,
    @NotNull WatchStatus watchStatus, @NotNull @Min(0) @Max(100) Integer progress,
    String description, Long coverFileId
) { }
