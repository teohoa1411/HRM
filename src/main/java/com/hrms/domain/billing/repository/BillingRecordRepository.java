package com.hrms.domain.billing.repository;

import com.hrms.domain.billing.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    List<BillingRecord> findByCompanyIdAndBillingMonth(Long companyId, String billingMonth);

    List<BillingRecord> findByCompanyIdAndBillingMonthBetween(Long companyId, String fromMonth, String toMonth);

    /**
     * @deprecated chỉ dùng được khi giả định 1 NV / 1 tháng = 1 record. Sau khi thêm period
     *             (V29), 1 NV có thể có nhiều record/tháng → dùng {@link #findByCompanyAndEmployeeAndMonth}.
     */
    @Deprecated
    Optional<BillingRecord> findByCompanyIdAndEmployeeIdAndBillingMonth(Long companyId, Long employeeId, String billingMonth);

    /** Xoá tất cả billing record của một NV trong một tháng (dùng trước khi recalc). */
    @Modifying
    @Query("""
        DELETE FROM BillingRecord b
        WHERE b.companyId = :companyId
          AND b.employeeId = :employeeId
          AND b.billingMonth = :billingMonth
        """)
    void deleteByCompanyAndEmployeeAndMonth(Long companyId, Long employeeId, String billingMonth);

    /** Tất cả billing record của một NV trong một tháng — có thể có nhiều period. */
    @Query("""
        SELECT b FROM BillingRecord b
        WHERE b.companyId = :companyId
          AND b.employeeId = :employeeId
          AND b.billingMonth = :billingMonth
        ORDER BY b.periodStart ASC
        """)
    List<BillingRecord> findByCompanyAndEmployeeAndMonth(Long companyId, Long employeeId, String billingMonth);

    @Query("""
        SELECT b FROM BillingRecord b
        WHERE b.companyId = :companyId
          AND b.billingMonth >= :fromMonth
          AND b.billingMonth <= :toMonth
        ORDER BY b.billingMonth, b.employeeId
        """)
    List<BillingRecord> findByCompanyAndDateRange(Long companyId, String fromMonth, String toMonth);

    @Query("""
        SELECT b FROM BillingRecord b
        WHERE b.companyId = :companyId
          AND b.billingMonth >= :fromMonth
          AND b.billingMonth <= :toMonth
          AND (:jobRoleId IS NULL OR b.jobRoleId = :jobRoleId)
          AND (:sourceType IS NULL OR b.sourceType = :sourceType)
        ORDER BY b.billingMonth, b.employeeId
        """)
    List<BillingRecord> findFiltered(Long companyId, String fromMonth, String toMonth,
                                     Long jobRoleId, String sourceType);
}
