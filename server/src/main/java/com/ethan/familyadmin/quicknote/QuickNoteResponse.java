package com.ethan.familyadmin.quicknote;

import java.time.LocalDateTime;
import java.util.List;

public record QuickNoteResponse(
    Long id, String title, String body, List<String> tags, List<Long> imageIds,
    List<QuickNotePayload.Task> tasks, String color, boolean pinned, String status,
    int version, Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt
) { }
