package com.ethan.familyadmin.file;

import com.ethan.familyadmin.common.api.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final LocalFileStorageService service;

    public FileController(LocalFileStorageService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileObjectResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.store(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        LoadedFile file = service.load(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mediaType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
            .body(new FileSystemResource(file.path()));
    }
}
