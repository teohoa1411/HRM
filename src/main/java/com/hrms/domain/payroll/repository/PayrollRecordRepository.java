package com.hrms.domain.payroll.repository;

import com.hrms.domain.payroll.entity.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {

    List<PayrollRecord> findByCompanyIdAndPayrollMonth(Long companyId, String payrollMonth);

    Optional<PayrollRecord> findByCompanyIdAndEmployeeIdAndPayrollMonth(
            Long companyId, Long employeeId, String payrollMonth);

    List<PayrollRecord> findByCompanyIdAndPayrollMonthAndStatus(
            Long companyId, String payrollMonth, String status);

    boolean existsByCompanyIdAndEmployeeIdAndPayrollMonthAndStatusIn(
            Long companyId, Long employeeId, String payrollMonth, List<String> statuses);

    @Query("SELECT COUNT(p) > 0 FROM PayrollRecord p WHERE p.companyId = :companyId " +
           "AND p.payrollMonth = :month AND p.status IN ('STALE')")
    boolean hasStaleRecords(@Param("companyId") Long companyId, @Param("month") String month);
}
