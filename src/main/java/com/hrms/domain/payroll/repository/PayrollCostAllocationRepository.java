package com.hrms.domain.payroll.repository;

import com.hrms.domain.payroll.entity.PayrollCostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PayrollCostAllocationRepository extends JpaRepository<PayrollCostAllocation, Long> {
    List<PayrollCostAllocation> findByCompanyIdAndPayrollMonth(Long companyId, String payrollMonth);
    List<PayrollCostAllocation> findByPayrollRecordId(Long payrollRecordId);
    @Modifying
    @Query("delete from PayrollCostAllocation a where a.companyId=:companyId and a.payrollMonth=:yearMonth")
    void deleteByCompanyIdAndPayrollMonth(@Param("companyId") Long companyId, @Param("yearMonth") String yearMonth);
}
