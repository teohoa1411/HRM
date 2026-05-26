package com.hrms.domain.document.sync.dto;

public record SyncTriggerResponse(
        Long sessionId,
        String status,
        String message
) {}
