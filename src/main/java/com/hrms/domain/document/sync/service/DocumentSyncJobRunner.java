package com.hrms.domain.document.sync.service;

import com.hrms.domain.common.enums.SyncStatus;
import com.hrms.domain.common.enums.SyncTargetType;
import com.hrms.domain.document.entity.Document;
import com.hrms.domain.document.repository.DocumentRepository;
import com.hrms.domain.document.sync.entity.DocumentSyncLog;
import com.hrms.domain.document.sync.entity.DocumentSyncSession;
import com.hrms.domain.document.sync.repository.DocumentSyncLogRepository;
import com.hrms.domain.document.sync.repository.DocumentSyncSessionRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.infrastructure.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tách riêng ra bean này để @Async được Spring proxy đúng cách.
 * DocumentSyncService không thể tự gọi @Async method của chính mình.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSyncJobRunner {

    private final DocumentSyncSessionRepository sessionRepository;
    private final DocumentSyncLogRepository     logRepository;
    private final DocumentRepository            documentRepository;
    private final EmployeeRepository            employeeRepository;
    private final FileStoragePort               storagePort;

    @Async
    public void run(Long sessionId, Long companyId) {
        DocumentSyncSession session = sessionRepository.findById(sessionId).orElseThrow();

        List<Document> docs = documentRepository.findByCompanyIdAndDeletedAtIsNull(companyId);

        List<Long> empIds = docs.stream().map(Document::getEmployeeId).distinct().toList();
        Map<Long, String> empCodeMap = employeeRepository.findAllById(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getEmployeeCode));

        int synced = 0, skipped = 0, failed = 0;

        for (Document doc : docs) {
            String empCode = empCodeMap.getOrDefault(doc.getEmployeeId(), "UNKNOWN_" + doc.getEmployeeId());
            DocumentSyncLog syncLog;

            try {
                syncLog = syncDocument(session, doc, empCode);
            } catch (Exception e) {
                log.error("Unexpected error syncing doc {}: {}", doc.getId(), e.getMessage(), e);
                syncLog = buildFailedLog(session, doc, empCode, e.getMessage());
            }

            logRepository.save(syncLog);
            switch (syncLog.getSyncStatus()) {
                case SUCCESS -> synced++;
                case SKIPPED -> skipped++;
                case FAILED  -> failed++;
            }
        }

        session.markCompleted(docs.size(), synced, skipped, failed);
        sessionRepository.save(session);
        log.info("Sync session {} completed: total={} synced={} skipped={} failed={}",
                sessionId, docs.size(), synced, skipped, failed);
    }

    private DocumentSyncLog syncDocument(DocumentSyncSession session, Document doc, String empCode) {
        Optional<DocumentSyncLog> lastSuccess = logRepository
                .findFirstByDocumentIdAndSyncStatusOrderBySyncedAtDesc(doc.getId(), SyncStatus.SUCCESS);

        if (lastSuccess.isPresent()) {
            try {
                String currentETag = storagePort.getETag(doc.getS3Key());
                if (currentETag != null && currentETag.equals(lastSuccess.get().getChecksum())) {
                    return DocumentSyncLog.builder()
                            .companyId(session.getCompanyId())
                            .sessionId(session.getId())
                            .documentId(doc.getId())
                            .employeeId(doc.getEmployeeId())
                            .employeeCode(empCode)
                            .s3Key(doc.getS3Key())
                            .targetPath(lastSuccess.get().getTargetPath())
                            .syncStatus(SyncStatus.SKIPPED)
                            .syncedAt(LocalDateTime.now())
                            .fileSize(doc.getFileSize())
                            .checksum(currentETag)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Could not get ETag for {}, will re-sync: {}", doc.getS3Key(), e.getMessage());
            }
        }

        return session.getTargetType() == SyncTargetType.S3_BUCKET
                ? syncToS3Bucket(session, doc, empCode)
                : syncToLocal(session, doc, empCode);
    }

    private DocumentSyncLog syncToLocal(DocumentSyncSession session, Document doc, String empCode) {
        String docType      = doc.getDocumentType().name().toLowerCase();
        String safeFileName = doc.getVersion() > 1 ? addVersionSuffix(doc.getFileName(), doc.getVersion()) : doc.getFileName();
        Path targetDir      = Paths.get(session.getTargetBasePath(), empCode, docType);
        Path targetFile     = targetDir.resolve(safeFileName);

        try {
            Files.createDirectories(targetDir);
            String etag = null;
            try { etag = storagePort.getETag(doc.getS3Key()); } catch (Exception ignored) {}

            try (InputStream in = storagePort.download(doc.getS3Key())) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return DocumentSyncLog.builder()
                    .companyId(session.getCompanyId()).sessionId(session.getId())
                    .documentId(doc.getId()).employeeId(doc.getEmployeeId()).employeeCode(empCode)
                    .s3Key(doc.getS3Key()).targetPath(targetFile.toString())
                    .syncStatus(SyncStatus.SUCCESS).syncedAt(LocalDateTime.now())
                    .fileSize(doc.getFileSize()).checksum(etag)
                    .build();

        } catch (Exception e) {
            log.error("Failed to sync doc {} to local {}: {}", doc.getId(), targetFile, e.getMessage());
            return buildFailedLog(session, doc, empCode, e.getMessage());
        }
    }

    private DocumentSyncLog syncToS3Bucket(DocumentSyncSession session, Document doc, String empCode) {
        String docType    = doc.getDocumentType().name().toLowerCase();
        String safeFileName = doc.getVersion() > 1 ? addVersionSuffix(doc.getFileName(), doc.getVersion()) : doc.getFileName();
        // backupBucket không có ở đây — inject qua properties
        // Lấy destBucket từ targetBasePath (format: s3://bucket-name/prefix)
        String targetPath = session.getTargetBasePath(); // e.g. "s3://hrms-backup/"
        String destBucket = targetPath.replace("s3://", "").split("/")[0];
        String destKey    = empCode + "/" + docType + "/" + safeFileName;

        try {
            String etag = null;
            try { etag = storagePort.getETag(doc.getS3Key()); } catch (Exception ignored) {}

            storagePort.copyObject(doc.getS3Key(), destBucket, destKey);

            return DocumentSyncLog.builder()
                    .companyId(session.getCompanyId()).sessionId(session.getId())
                    .documentId(doc.getId()).employeeId(doc.getEmployeeId()).employeeCode(empCode)
                    .s3Key(doc.getS3Key()).targetPath("s3://" + destBucket + "/" + destKey)
                    .syncStatus(SyncStatus.SUCCESS).syncedAt(LocalDateTime.now())
                    .fileSize(doc.getFileSize()).checksum(etag)
                    .build();

        } catch (Exception e) {
            log.error("Failed to copy doc {} to S3 bucket {}: {}", doc.getId(), destBucket, e.getMessage());
            return buildFailedLog(session, doc, empCode, e.getMessage());
        }
    }

    private DocumentSyncLog buildFailedLog(DocumentSyncSession session, Document doc, String empCode, String error) {
        String msg = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        return DocumentSyncLog.builder()
                .companyId(session.getCompanyId()).sessionId(session.getId())
                .documentId(doc.getId()).employeeId(doc.getEmployeeId()).employeeCode(empCode)
                .s3Key(doc.getS3Key()).syncStatus(SyncStatus.FAILED)
                .syncedAt(LocalDateTime.now()).fileSize(doc.getFileSize()).errorMessage(msg)
                .build();
    }

    private String addVersionSuffix(String fileName, int version) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return fileName + "_v" + version;
        return fileName.substring(0, dot) + "_v" + version + fileName.substring(dot);
    }
}
