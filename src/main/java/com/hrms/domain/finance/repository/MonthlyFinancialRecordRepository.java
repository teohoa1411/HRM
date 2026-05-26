package com.hrms.domain.finance.repository;

import com.hrms.domain.finance.entity.MonthlyFinancialRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MonthlyFinancialRecordRepository extends JpaRepository<MonthlyFinancialRecord, Long> {

    List<MonthlyFinancialRecord> findByCompanyIdAndRecordMonth(Long companyId, String month);

    List<MonthlyFinancialRecord> findByCompanyIdAndRecordMonthAndCustomerId(
            Long companyId, String month, Long customerId);

    List<MonthlyFinancialRecord> findByCompanyIdAndRecordMonthAndVendorId(
            Long companyId, String month, Long vendorId);

    @Query("SELECT SUM(r.revenueAmount) FROM MonthlyFinancialRecord r " +
           "WHERE r.companyId = :companyId AND r.recordMonth = :month")
    BigDecimal sumRevenueByCompanyAndMonth(@Param("companyId") Long companyId, @Param("month") String month);

    @Query("SELECT SUM(r.netCostAmount) FROM MonthlyFinancialRecord r " +
           "WHERE r.companyId = :companyId AND r.recordMonth = :month AND r.sourceType = 'INTERNAL'")
    BigDecimal sumPayrollCostByCompanyAndMonth(@Param("companyId") Long companyId, @Param("month") String month);

    @Query("SELECT SUM(r.netCostAmount) FROM MonthlyFinancialRecord r " +
           "WHERE r.companyId = :companyId AND r.recordMonth = :month AND r.sourceType = 'VENDOR'")
    BigDecimal sumVendorCostByCompanyAndMonth(@Param("companyId") Long companyId, @Param("month") String month);
}
