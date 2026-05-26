package com.hrms.domain.leave.repository;

import com.hrms.domain.leave.entity.LeaveRequest;
import com.hrms.domain.leave.entity.LeaveRequest.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Page<LeaveRequest> findByCompanyIdAndEmployeeIdAndDeletedAtIsNull(Long companyId, Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByCompanyIdAndEmployeeIdAndStatusAndDeletedAtIsNull(Long companyId, Long employeeId, LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, LeaveStatus status, Pageable pageable);

    Optional<LeaveRequest> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    List<LeaveRequest> findByEmployeeIdAndStatusAndDeletedAtIsNull(Long employeeId, LeaveStatus status);

    /** Kiểm tra trùng ngày: có đơn APPROVED/PENDING nào của nhân sự đè lên khoảng [from, to] không */
    @Query("""
        SELECT COUNT(lr) FROM LeaveRequest lr
        WHERE lr.companyId = :companyId
          AND lr.employeeId = :employeeId
          AND lr.status IN ('APPROVED', 'PENDING')
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :to
          AND lr.endDate >= :from
        """)
    long countOverlapping(@Param("companyId") Long companyId,
                          @Param("employeeId") Long employeeId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /** Approved + Pending leaves overlapping a date range (for timesheet icon display) */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.companyId = :companyId
          AND lr.employeeId = :employeeId
          AND lr.status IN ('APPROVED', 'PENDING')
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :to
          AND lr.endDate >= :from
        """)
    List<LeaveRequest> findApprovedByEmployeeAndDateRange(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Chỉ lấy APPROVED leaves — dùng cho biên bản nội bộ (không lấy PENDING) */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.companyId = :companyId
          AND lr.employeeId = :employeeId
          AND lr.status = 'APPROVED'
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :to
          AND lr.endDate >= :from
        """)
    List<LeaveRequest> findOnlyApprovedByEmployeeAndDateRange(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Tổng ngày nghỉ đã duyệt của nhân sự trong tháng — dùng cho tính billing */
    @Query("""
        SELECT COALESCE(SUM(lr.totalDays), 0)
        FROM LeaveRequest lr
        WHERE lr.employeeId = :employeeId
          AND lr.status = 'APPROVED'
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :monthEnd
          AND lr.endDate >= :monthStart
        """)
    BigDecimal sumApprovedDaysInMonth(Long employeeId, LocalDate monthStart, LocalDate monthEnd);

    /**
     * Tổng ngày nghỉ CÓ LƯƠNG (is_paid = true) đã duyệt trong tháng.
     * Dùng để tính lương nội bộ: nghỉ phép có lương = vẫn tính lương đủ.
     */
    @Query("""
        SELECT COALESCE(SUM(lr.totalDays), 0)
        FROM LeaveRequest lr
        JOIN LeaveType lt ON lt.id = lr.leaveTypeId
        WHERE lr.employeeId = :employeeId
          AND lr.status = 'APPROVED'
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :monthEnd
          AND lr.endDate >= :monthStart
          AND lt.paid = true
        """)
    BigDecimal sumPaidApprovedDaysInMonth(@Param("employeeId") Long employeeId,
                                          @Param("monthStart") LocalDate monthStart,
                                          @Param("monthEnd") LocalDate monthEnd);

    /**
     * Tổng ngày nghỉ KHÔNG LƯƠNG (is_paid = false) đã duyệt trong tháng.
     * Dùng để tính khấu trừ: nghỉ không lương → trừ lương / trừ tiền khách hàng.
     */
    @Query("""
        SELECT COALESCE(SUM(lr.totalDays), 0)
        FROM LeaveRequest lr
        JOIN LeaveType lt ON lt.id = lr.leaveTypeId
        WHERE lr.employeeId = :employeeId
          AND lr.status = 'APPROVED'
          AND lr.deletedAt IS NULL
          AND lr.startDate <= :monthEnd
          AND lr.endDate >= :monthStart
          AND lt.paid = false
        """)
    BigDecimal sumUnpaidApprovedDaysInMonth(@Param("employeeId") Long employeeId,
                                             @Param("monthStart") LocalDate monthStart,
                                             @Param("monthEnd") LocalDate monthEnd);
}
