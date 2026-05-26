package com.hrms.domain.statement.repository;

import com.hrms.domain.statement.entity.StatementLineItemDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatementLineItemDailyRepository extends JpaRepository<StatementLineItemDaily, Long> {

    List<StatementLineItemDaily> findByStatementLineItemId(Long statementLineItemId);

    Optional<StatementLineItemDaily> findByStatementLineItemIdAndWorkDate(Long statementLineItemId, LocalDate workDate);

    List<StatementLineItemDaily> findByCompanyIdAndEmployeeIdAndWorkDateBetween(
            Long companyId, Long employeeId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM StatementLineItemDaily d WHERE d.statementLineItemId = :lineItemId")
    void deleteByStatementLineItemId(@Param("lineItemId") Long lineItemId);
}
