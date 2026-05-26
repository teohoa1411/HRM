package com.hrms.domain.payroll.repository;

import com.hrms.domain.payroll.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Long> {

    List<PayrollAdjustment> findByPayrollRecordIdOrderBySortOrderAsc(Long payrollRecordId);

    void deleteByPayrollRecordId(Long payrollRecordId);
}
