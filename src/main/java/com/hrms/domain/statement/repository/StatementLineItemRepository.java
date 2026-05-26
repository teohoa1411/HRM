package com.hrms.domain.statement.repository;

import com.hrms.domain.statement.entity.StatementLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StatementLineItemRepository extends JpaRepository<StatementLineItem, Long> {

    List<StatementLineItem> findByStatement_Id(Long statementId);

    @Query("SELECT li FROM StatementLineItem li WHERE li.statement.companyId = :companyId " +
           "AND li.employeeId = :employeeId AND li.statement.statementMonth = :month " +
           "AND li.statement.status IN ('SENT','CONFIRMED')")
    List<StatementLineItem> findSentByEmployeeAndMonth(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("month") String month);
}
