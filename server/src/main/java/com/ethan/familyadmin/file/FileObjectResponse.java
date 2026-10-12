package com.ethan.familyadmin.file;

public record FileObjectResponse(
    Long id,
    String originalName,
    String storageKey,
    String mediaType,
    long sizeBytes
) {
}
