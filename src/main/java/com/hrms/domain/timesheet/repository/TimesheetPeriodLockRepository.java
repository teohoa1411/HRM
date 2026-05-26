package com.hrms.domain.timesheet.repository;

import com.hrms.domain.timesheet.entity.TimesheetPeriodLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimesheetPeriodLockRepository extends JpaRepository<TimesheetPeriodLock, Long> {

    Optional<TimesheetPeriodLock> findByCompanyIdAndYearMonth(Long companyId, String yearMonth);

    boolean existsByCompanyIdAndYearMonthAndLockedTrue(Long companyId, String yearMonth);
}
