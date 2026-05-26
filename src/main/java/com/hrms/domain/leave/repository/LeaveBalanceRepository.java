package com.hrms.domain.leave.repository;

import com.hrms.domain.leave.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByCompanyIdAndEmployeeIdAndLeaveTypeIdAndYear(
            Long companyId, Long employeeId, Long leaveTypeId, int year);

    /** Bug 3: Pessimistic lock — chống race condition khi 2 admin approve cùng lúc */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT lb FROM LeaveBalance lb
        WHERE lb.companyId = :companyId
          AND lb.employeeId = :employeeId
          AND lb.leaveTypeId = :leaveTypeId
          AND lb.year = :year
        """)
    Optional<LeaveBalance> findForUpdate(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("year") int year);

    List<LeaveBalance> findByCompanyIdAndEmployeeIdAndYear(
            Long companyId, Long employeeId, int year);

    List<LeaveBalance> findByCompanyIdAndEmployeeId(Long companyId, Long employeeId);

    /**
     * Lấy tất cả leave_balances của danh sách nhân sự trong năm.
     * Dùng khi tính bảng lương hoặc báo cáo nghỉ phép hàng loạt.
     */
    @Query("""
        SELECT lb FROM LeaveBalance lb
        WHERE lb.companyId = :companyId
          AND lb.year = :year
          AND lb.employeeId IN :employeeIds
        """)
    List<LeaveBalance> findByCompanyIdAndYearAndEmployeeIdIn(
            @Param("companyId") Long companyId,
            @Param("year") int year,
            @Param("employeeIds") List<Long> employeeIds);

    /**
     * Tìm balance cho loại phép CÓ HƯỞNG LƯƠNG (is_paid=true) của nhân sự trong năm.
     * Dùng khi tính paid_leave_days cho billing_records.
     */
    @Query("""
        SELECT lb FROM LeaveBalance lb
        JOIN LeaveType lt ON lt.id = lb.leaveTypeId
        WHERE lb.companyId = :companyId
          AND lb.employeeId = :employeeId
          AND lb.year = :year
          AND lt.paid = true
        """)
    List<LeaveBalance> findPaidLeaveBalances(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("year") int year);
}
