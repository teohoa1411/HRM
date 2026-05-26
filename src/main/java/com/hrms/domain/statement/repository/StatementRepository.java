package com.hrms.domain.statement.repository;

import com.hrms.domain.statement.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByCompanyIdAndStatementMonthAndStatementType(
            Long companyId, String month, String type);

    /** Tìm các biên bản trong tháng theo danh sách status (dùng khi Kế toán revert xác nhận timesheet). */
    List<Statement> findByCompanyIdAndStatementMonthAndStatusIn(
            Long companyId, String month, List<String> statuses);

    Optional<Statement> findByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonth(
            Long companyId, String type, Long recipientId, String month);

    boolean existsByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonthAndStatusIn(
            Long companyId, String type, Long recipientId, String month, List<String> statuses);

    /**
     * Tìm các biên bản đã xuất (EXPORTED/SENT/CONFIRMED) có line item của employee này trong tháng.
     * Dùng để đánh dấu STALE khi timesheet bị chỉnh sửa sau khi xuất.
     */
    @Query("SELECT DISTINCT s FROM Statement s JOIN s.lineItems li " +
           "WHERE s.companyId = :companyId AND s.statementMonth = :month " +
           "AND li.employeeId = :employeeId AND s.status IN ('EXPORTED','SENT','CONFIRMED')")
    List<Statement> findExportedByEmployeeAndMonth(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("month") String month);
}
