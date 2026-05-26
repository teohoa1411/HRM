package com.hrms.domain.document.sync.service;

import com.hrms.config.DocumentSyncProperties;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.document.sync.dto.SyncLogDto;
import com.hrms.domain.document.sync.dto.SyncSessionDto;
import com.hrms.domain.document.sync.dto.SyncTriggerResponse;
import com.hrms.domain.document.sync.entity.DocumentSyncSession;
import com.hrms.domain.document.sync.entity.DocumentSyncLog;
import com.hrms.domain.document.sync.repository.DocumentSyncLogRepository;
import com.hrms.domain.document.sync.repository.DocumentSyncSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSyncService {

    private final DocumentSyncSessionRepository sessionRepository;
    private final DocumentSyncLogRepository     logRepository;
    private final DocumentSyncJobRunner         jobRunner;
    private final DocumentSyncProperties        syncProperties;

    /**
     * Admin bấm nút Đồng bộ — tạo session rồi kick async job.
     * jobRunner.run() là @Async trên bean khác → Spring proxy hoạt động đúng.
     */
    @Transactional
    public SyncTriggerResponse triggerSync(Long companyId, Long triggeredBy) {
        if (!syncProperties.isEnabled()) {
            throw new BusinessException("Document sync is disabled", HttpStatus.BAD_REQUEST, "SYNC_DISABLED");
        }

        // Ngăn double-run
        Optional<DocumentSyncSession> running = sessionRepository
                .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, "RUNNING");
        if (running.isPresent()) {
            return new SyncTriggerResponse(
                    running.get().getId(), "RUNNING",
                    "Đang có phiên đồng bộ chạy (id=" + running.get().getId() + "). Vui lòng chờ hoàn tất.");
        }

        DocumentSyncSession session = DocumentSyncSession.builder()
                .companyId(companyId)
                .triggeredBy(triggeredBy)
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .targetType(syncProperties.getTargetType())
                .targetBasePath(syncProperties.getTargetPath())
                .build();
        session = sessionRepository.save(session);

        // QUAN TRỌNG: phải đợi transaction commit xong mới chạy async.
        // Nếu gọi jobRunner.run() ngay, async thread có thể findById trước khi
        // row được commit → NoSuchElementException.
        final Long sessionId = session.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobRunner.run(sessionId, companyId);
            }
        });

        return new SyncTriggerResponse(session.getId(), "RUNNING", "Đồng bộ đã bắt đầu");
    }

    @Transactional(readOnly = true)
    public Optional<SyncSessionDto> getRunningSession(Long companyId) {
        return sessionRepository
                .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, "RUNNING")
                .map(this::toSessionDto);
    }

    @Transactional(readOnly = true)
    public Page<SyncSessionDto> getHistory(Long companyId, Pageable pageable) {
        return sessionRepository
                .findByCompanyIdOrderByStartedAtDesc(companyId, pageable)
                .map(this::toSessionDto);
    }

    @Transactional(readOnly = true)
    public Page<SyncLogDto> getSessionLogs(Long sessionId, Long companyId, Pageable pageable) {
        sessionRepository.findByIdAndCompanyId(sessionId, companyId)
                .orElseThrow(() -> new BusinessException("Session not found", HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        return logRepository.findBySessionIdOrderBySyncedAtDesc(sessionId, pageable)
                .map(this::toLogDto);
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private SyncSessionDto toSessionDto(DocumentSyncSession s) {
        return new SyncSessionDto(
                s.getId(), s.getTriggeredBy(), s.getStartedAt(), s.getCompletedAt(),
                s.getStatus(), s.getTotalDocuments(), s.getSyncedCount(),
                s.getSkippedCount(), s.getFailedCount(),
                s.getTargetType().name(), s.getTargetBasePath());
    }

    private SyncLogDto toLogDto(DocumentSyncLog l) {
        return new SyncLogDto(
                l.getId(), l.getDocumentId(), l.getEmployeeId(), l.getEmployeeCode(),
                l.getS3Key(), l.getTargetPath(), l.getSyncStatus().name(),
                l.getSyncedAt(), l.getErrorMessage(), l.getFileSize(), l.getChecksum());
    }
}
