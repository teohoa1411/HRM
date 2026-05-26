package com.hrms.domain.document.sync.dto;

import java.time.LocalDateTime;

public record SyncSessionDto(
        Long id,
        Long triggeredBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        int totalDocuments,
        int syncedCount,
        int skippedCount,
        int failedCount,
        String targetType,
        String targetBasePath
) {}
