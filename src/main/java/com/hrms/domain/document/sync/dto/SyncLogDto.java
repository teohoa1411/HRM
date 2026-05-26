package com.hrms.domain.document.sync.dto;

import java.time.LocalDateTime;

public record SyncLogDto(
        Long id,
        Long documentId,
        Long employeeId,
        String employeeCode,
        String s3Key,
        String targetPath,
        String syncStatus,
        LocalDateTime syncedAt,
        String errorMessage,
        Long fileSize,
        String checksum
) {}
