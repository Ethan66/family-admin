package com.ethan.familyadmin.photo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PhotoResponse(Long id, String title, String album, LocalDate photoDate, String description,
                            Long fileId, Long createdBy, LocalDateTime createdAt) {
}
