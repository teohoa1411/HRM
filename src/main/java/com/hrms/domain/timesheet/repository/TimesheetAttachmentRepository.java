package com.hrms.domain.timesheet.repository;

import com.hrms.domain.timesheet.entity.TimesheetAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TimesheetAttachmentRepository extends JpaRepository<TimesheetAttachment, Long> {
    List<TimesheetAttachment> findByUserIdAndYearMonthOrderByCreatedAtDesc(Long userId, String yearMonth);
    Optional<TimesheetAttachment> findByIdAndCompanyId(Long id, Long companyId);
}
