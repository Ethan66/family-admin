package com.ethan.familyadmin.file;

import java.nio.file.Path;

public record LoadedFile(Path path, String originalName, String mediaType) {
}
