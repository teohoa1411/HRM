package com.hrms.domain.timesheet.repository;

import com.hrms.domain.timesheet.entity.TimesheetMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TimesheetMonthlyRepository extends JpaRepository<TimesheetMonthly, Long> {
    Optional<TimesheetMonthly> findByUserIdAndYearMonth(Long userId, String yearMonth);
    java.util.List<TimesheetMonthly> findByCompanyIdAndYearMonth(Long companyId, String yearMonth);
}
