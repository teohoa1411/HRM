package com.hrms.domain.timesheet.repository;

import com.hrms.domain.timesheet.dto.EmployeeTimesheetSummary;
import com.hrms.domain.timesheet.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    @Query("SELECT t FROM Timesheet t WHERE t.userId = :userId AND t.companyId = :companyId " +
           "AND t.workDate >= :from AND t.workDate <= :to AND t.deletedAt IS NULL " +
           "ORDER BY t.projectKey, t.workDate")
    List<Timesheet> findByUserAndMonth(@Param("userId") Long userId,
                                       @Param("companyId") Long companyId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    Optional<Timesheet> findByUserIdAndProjectKeyAndWorkDateAndDeletedAtIsNull(
            Long userId, String projectKey, LocalDate workDate);

    Optional<Timesheet> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    @Query("SELECT t FROM Timesheet t WHERE t.userId = :userId AND t.companyId = :companyId " +
           "AND t.projectKey = :projectKey AND t.workDate >= :from AND t.workDate <= :to " +
           "AND t.deletedAt IS NULL")
    List<Timesheet> findByUserProjectAndMonth(@Param("userId") Long userId,
                                              @Param("companyId") Long companyId,
                                              @Param("projectKey") String projectKey,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** Tìm tất cả entry của userId trong 1 ngày (không phân biệt project) */
    @Query("SELECT t FROM Timesheet t WHERE t.userId = :userId AND t.workDate = :workDate AND t.deletedAt IS NULL")
    List<Timesheet> findByUserIdAndWorkDateAndDeletedAtIsNull(@Param("userId") Long userId,
                                                              @Param("workDate") LocalDate workDate);

    /**
     * Tổng quan cho tháng CHƯA khóa:
     * - Người đã có log giờ trong tháng, HOẶC
     * - Nhân sự ACTIVE/PROBATION đã onboard trước cuối tháng (kể cả chưa log)
     */
    @Query("""
        SELECT new com.hrms.domain.timesheet.dto.EmployeeTimesheetSummary(
            u.id, u.username,
            COALESCE(CONCAT(e.firstName, ' ', e.lastName), u.username),
            COALESCE(SUM(t.hours), 0),
            COALESCE(SUM(t.otHours), 0),
            tm.description,
            tm.additions
        )
        FROM com.hrms.domain.account.entity.User u
        LEFT JOIN com.hrms.domain.employee.entity.Employee e ON e.userId = u.id AND e.deletedAt IS NULL
        LEFT JOIN Timesheet t ON t.userId = u.id
            AND t.workDate >= :from AND t.workDate <= :to AND t.deletedAt IS NULL
        LEFT JOIN com.hrms.domain.timesheet.entity.TimesheetMonthly tm
            ON tm.userId = u.id AND tm.yearMonth = :yearMonth
        WHERE u.companyId = :companyId
        AND e.id IS NOT NULL
        AND (
            EXISTS (SELECT t2 FROM Timesheet t2 WHERE t2.userId = u.id
                    AND t2.workDate >= :from AND t2.workDate <= :to AND t2.deletedAt IS NULL)
            OR (e.status IN ('ACTIVE', 'PROBATION')
                AND e.startDate <= :to)
        )
        GROUP BY u.id, u.username, e.firstName, e.lastName, tm.description, tm.additions
        ORDER BY u.username
    """)
    List<EmployeeTimesheetSummary> adminOverviewUnlocked(@Param("companyId") Long companyId,
                                                          @Param("from") LocalDate from,
                                                          @Param("to") LocalDate to,
                                                          @Param("yearMonth") String yearMonth);

    /**
     * Tổng quan cho tháng ĐÃ KHÓA:
     * - Chỉ hiển thị người đã có log giờ (trạng thái tại thời điểm khóa)
     */
    @Query("""
        SELECT new com.hrms.domain.timesheet.dto.EmployeeTimesheetSummary(
            u.id, u.username,
            COALESCE(CONCAT(e.firstName, ' ', e.lastName), u.username),
            COALESCE(SUM(t.hours), 0),
            COALESCE(SUM(t.otHours), 0),
            tm.description,
            tm.additions
        )
        FROM com.hrms.domain.account.entity.User u
        LEFT JOIN com.hrms.domain.employee.entity.Employee e ON e.userId = u.id AND e.deletedAt IS NULL
        JOIN Timesheet t ON t.userId = u.id
            AND t.workDate >= :from AND t.workDate <= :to AND t.deletedAt IS NULL
        LEFT JOIN com.hrms.domain.timesheet.entity.TimesheetMonthly tm
            ON tm.userId = u.id AND tm.yearMonth = :yearMonth
        WHERE u.companyId = :companyId
        GROUP BY u.id, u.username, e.firstName, e.lastName, tm.description, tm.additions
        ORDER BY u.username
    """)
    List<EmployeeTimesheetSummary> adminOverviewLocked(@Param("companyId") Long companyId,
                                                        @Param("from") LocalDate from,
                                                        @Param("to") LocalDate to,
                                                        @Param("yearMonth") String yearMonth);
}
