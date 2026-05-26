package com.hrms.domain.assignment.repository;

import com.hrms.domain.assignment.entity.EmployeeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    List<EmployeeAssignment> findByEmployeeIdAndDeletedAtIsNull(Long employeeId);

    List<EmployeeAssignment> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    Optional<EmployeeAssignment> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    /** Tìm phân công đang có hiệu lực của nhân sự tại thời điểm refDate */
    @Query("""
        SELECT a FROM EmployeeAssignment a
        WHERE a.employeeId = :employeeId
          AND a.deletedAt IS NULL
          AND a.startDate <= :refDate
          AND (a.endDate IS NULL OR a.endDate >= :refDate)
        ORDER BY a.startDate DESC
        LIMIT 1
        """)
    Optional<EmployeeAssignment> findActiveAt(Long employeeId, LocalDate refDate);

    /**
     * Tìm tất cả assignment có phần overlap với khoảng [rangeStart, rangeEnd].
     * Dùng để tách tháng thành các period theo customer.
     */
    @Query("""
        SELECT a FROM EmployeeAssignment a
        WHERE a.employeeId = :employeeId
          AND a.deletedAt IS NULL
          AND a.startDate <= :rangeEnd
          AND (a.endDate IS NULL OR a.endDate >= :rangeStart)
        ORDER BY a.startDate ASC
        """)
    List<EmployeeAssignment> findOverlapping(Long employeeId, LocalDate rangeStart, LocalDate rangeEnd);

    /**
     * Kiểm tra có assignment nào (ngoại trừ excludeId) overlap với [rangeStart, rangeEnd] không.
     * Dùng cho validation khi tạo/sửa assignment để tránh vi phạm rule "1 NV / 1 ngày = 1 customer".
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM EmployeeAssignment a
        WHERE a.employeeId = :employeeId
          AND a.deletedAt IS NULL
          AND a.id <> :excludeId
          AND a.startDate <= :rangeEnd
          AND (a.endDate IS NULL OR a.endDate >= :rangeStart)
        """)
    boolean existsOverlap(Long employeeId, LocalDate rangeStart, LocalDate rangeEnd, Long excludeId);

    /**
     * Bulk-load assignments for a set of employees at a specific customer that overlap with [monthStart, monthEnd].
     * Ordered startDate DESC so putIfAbsent keeps the most recent one per employee.
     */
    @Query("""
        SELECT a FROM EmployeeAssignment a
        WHERE a.customerId = :customerId
          AND a.employeeId IN :employeeIds
          AND a.deletedAt IS NULL
          AND a.startDate <= :monthEnd
          AND (a.endDate IS NULL OR a.endDate >= :monthStart)
        ORDER BY a.employeeId ASC, a.startDate DESC
        """)
    List<EmployeeAssignment> findByCustomerAndEmployeesInMonth(
            @Param("customerId") Long customerId,
            @Param("employeeIds") Collection<Long> employeeIds,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);
}
