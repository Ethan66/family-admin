package com.ethan.familyadmin.quicknote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record QuickNotePayload(
    @NotNull @Size(max = 200) String title,
    @NotNull @Size(max = 10000) String body,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 30) String> tags,
    @NotNull @Size(max = 9) List<@NotNull @Positive Long> imageIds,
    @NotNull @Size(max = 100) List<@NotNull @Valid Task> tasks,
    @NotNull @Pattern(regexp = "plain|sage|sand") String color,
    @NotNull Boolean pinned,
    @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
    @NotNull @Min(0) Integer version
) {
    public record Task(@NotBlank @Size(max = 300) String text, boolean done) { }
}
