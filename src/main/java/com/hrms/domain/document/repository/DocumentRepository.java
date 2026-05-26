package com.hrms.domain.document.repository;

import com.hrms.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByEmployeeIdAndCompanyIdAndDeletedAtIsNull(Long employeeId, Long companyId);

    Optional<Document> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    // Dùng cho Document Sync: lấy tất cả document chưa xóa của 1 công ty
    List<Document> findByCompanyIdAndDeletedAtIsNull(Long companyId);
}
