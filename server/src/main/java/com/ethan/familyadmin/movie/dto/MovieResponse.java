package com.ethan.familyadmin.movie.dto;

import com.ethan.familyadmin.movie.WatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovieResponse(Long id, String title, String type, Integer releaseYear, String director,
    Integer minutes, BigDecimal rating, WatchStatus watchStatus, Integer progress, String description,
    Long coverFileId, Long createdBy, LocalDateTime createdAt) { }
