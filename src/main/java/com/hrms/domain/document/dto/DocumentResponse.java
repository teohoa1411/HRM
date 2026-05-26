package com.hrms.domain.document.dto;

import com.hrms.domain.common.enums.DocumentType;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long employeeId,
        DocumentType documentType,
        String fileName,
        String s3Key,
        Long fileSize,
        String mimeType,
        int version,
        Long uploadedBy,
        LocalDateTime createdAt
) {}
