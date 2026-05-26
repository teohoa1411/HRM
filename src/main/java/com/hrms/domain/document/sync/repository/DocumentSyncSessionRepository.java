package com.hrms.domain.document.sync.repository;

import com.hrms.domain.document.sync.entity.DocumentSyncSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSyncSessionRepository extends JpaRepository<DocumentSyncSession, Long> {

    Optional<DocumentSyncSession> findFirstByCompanyIdAndStatusOrderByStartedAtDesc(Long companyId, String status);

    Page<DocumentSyncSession> findByCompanyIdOrderByStartedAtDesc(Long companyId, Pageable pageable);

    Optional<DocumentSyncSession> findByIdAndCompanyId(Long id, Long companyId);
}
