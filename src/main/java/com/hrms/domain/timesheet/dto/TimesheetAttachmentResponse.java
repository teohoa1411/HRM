package com.hrms.domain.timesheet.dto;

import com.hrms.domain.timesheet.entity.TimesheetAttachment;

import java.time.LocalDateTime;

public record TimesheetAttachmentResponse(
        Long id,
        String fileName,
        Long fileSize,
        String mimeType,
        String yearMonth,
        LocalDateTime createdAt
) {
    public static TimesheetAttachmentResponse from(TimesheetAttachment a) {
        return new TimesheetAttachmentResponse(
                a.getId(), a.getFileName(), a.getFileSize(),
                a.getMimeType(), a.getYearMonth(), a.getCreatedAt());
    }
}
