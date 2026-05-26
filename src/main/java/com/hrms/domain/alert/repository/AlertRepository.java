package com.hrms.domain.alert.repository;

import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.domain.common.enums.AlertSeverity;
import com.hrms.domain.common.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** Dashboard: tất cả alerts ACTIVE, sort severity DESC + due_date ASC */
    List<Alert> findByCompanyIdAndStatusOrderBySeverityAscDueDateAsc(Long companyId, AlertStatus status);

    /** Filter theo category */
    List<Alert> findByCompanyIdAndCategoryAndStatusOrderByDueDateAsc(
            Long companyId, AlertCategory category, AlertStatus status);

    /** Đếm theo severity (cho badge) */
    long countByCompanyIdAndStatusAndSeverity(Long companyId, AlertStatus status, AlertSeverity severity);

    /** Check alert đã tồn tại (upsert) */
    Optional<Alert> findByCompanyIdAndAlertKey(Long companyId, String alertKey);

    /** Tìm theo ID + company (security) */
    Optional<Alert> findByIdAndCompanyId(Long id, Long companyId);

    /** Lấy alerts ACTIVE theo category (cho auto-resolve) */
    List<Alert> findByCompanyIdAndCategoryAndStatus(Long companyId, AlertCategory category, AlertStatus status);

    /** Cleanup: DISMISSED/RESOLVED quá N ngày */
    @Modifying
    @Query("DELETE FROM Alert a WHERE a.companyId = :companyId AND a.status IN ('DISMISSED','RESOLVED') AND a.updatedAt < :before")
    int cleanupOld(@Param("companyId") Long companyId, @Param("before") LocalDateTime before);
}
