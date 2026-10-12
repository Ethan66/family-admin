package com.ethan.familyadmin.note;

import java.time.LocalDateTime;

public record StudyNoteResponse(Long id, String title, String tag, String markdownBody, Long createdBy,
                                LocalDateTime createdAt, LocalDateTime updatedAt) { }
