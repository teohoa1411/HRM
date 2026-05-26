package com.hrms.domain.payroll.repository;

import com.hrms.domain.payroll.entity.PayrollRecordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRecordHistoryRepository extends JpaRepository<PayrollRecordHistory, Long> {
    List<PayrollRecordHistory> findByPayrollRecordIdOrderByChangedAtDesc(Long payrollRecordId);
}
