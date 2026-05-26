package com.hrms.domain.account.service;

import com.hrms.domain.account.entity.AuditLog;
import com.hrms.domain.account.repository.AuditLogRepository;
import com.hrms.domain.common.enums.AuditAction;
import com.hrms.infrastructure.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Main logging method - creates and saves an audit log entry.
     * Wrapped in try-catch to ensure audit failure never breaks business logic.
     *
     * @param companyId The company ID
     * @param userId The user ID
     * @param username The username
     * @param action The audit action
     * @param entityType The entity type being audited
     * @param entityId The entity ID
     * @param oldValue The old value (before change)
     * @param newValue The new value (after change)
     * @param description Description of the action
     * @param ipAddress Client IP address
     * @param userAgent User agent string
     * @param status Status of the action (SUCCESS, FAILED, etc.)
     */
    public void log(Long companyId, Long userId, String username, AuditAction action,
                    String entityType, Long entityId, String oldValue, String newValue,
                    String description, String ipAddress, String userAgent, String status) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .companyId(companyId)
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entityType={}, error={}",
                    action, entityType, e.getMessage(), e);
        }
    }

    /**
     * Convenience method to log from HTTP request context.
     * Extracts userId, username, companyId from current security context.
     * Extracts IP and User-Agent from request headers.
     *
     * @param action The audit action
     * @param entityType The entity type being audited
     * @param entityId The entity ID
     * @param description Description of the action
     * @param request The HTTP request
     */
    public void logFromContext(AuditAction action, String entityType, Long entityId,
                              String description, HttpServletRequest request) {
        logFromContext(action, entityType, entityId, null, null, description, request);
    }

    /**
     * Convenience method to log from HTTP request context with old/new values.
     * Extracts userId, username, companyId from current security context.
     * Extracts IP and User-Agent from request headers.
     *
     * @param action The audit action
     * @param entityType The entity type being audited
     * @param entityId The entity ID
     * @param oldValue The old value (before change)
     * @param newValue The new value (after change)
     * @param description Description of the action
     * @param request The HTTP request
     */
    public void logFromContext(AuditAction action, String entityType, Long entityId,
                              String oldValue, String newValue, String description,
                              HttpServletRequest request) {
        try {
            // Extract user info from security context
            Long userId = null;
            Long companyId = null;
            String username = null;

            try {
                var currentUser = SecurityUtils.getCurrentUser();
                userId = currentUser.userId();
                companyId = currentUser.companyId();
                username = currentUser.username();
            } catch (IllegalStateException e) {
                // User is not authenticated, continue with null values
                log.debug("No authenticated user for audit log context");
            }

            String ipAddress = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");

            log(companyId, userId, username, action, entityType, entityId,
                    oldValue, newValue, description, ipAddress, userAgent, "SUCCESS");
        } catch (Exception e) {
            log.error("Failed to log from context: action={}, entityType={}, error={}",
                    action, entityType, e.getMessage(), e);
        }
    }

    /**
     * Static helper method to extract client IP from request.
     * Checks X-Forwarded-For header first (for proxies), then falls back to remoteAddr.
     *
     * @param request The HTTP request
     * @return The client IP address
     */
    public static String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first (for proxies)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "UNKNOWN";
    }
}
