package com.hrms.domain.document.sync.repository;

import com.hrms.domain.common.enums.SyncStatus;
import com.hrms.domain.document.sync.entity.DocumentSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSyncLogRepository extends JpaRepository<DocumentSyncLog, Long> {

    // Lấy log mới nhất của 1 document (để kiểm tra incremental sync)
    Optional<DocumentSyncLog> findFirstByDocumentIdAndSyncStatusOrderBySyncedAtDesc(Long documentId, SyncStatus syncStatus);

    Page<DocumentSyncLog> findBySessionIdOrderBySyncedAtDesc(Long sessionId, Pageable pageable);
}
