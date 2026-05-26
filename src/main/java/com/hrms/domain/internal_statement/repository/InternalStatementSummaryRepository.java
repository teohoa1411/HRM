package com.hrms.domain.internal_statement.repository;

import com.hrms.domain.internal_statement.entity.InternalStatementSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InternalStatementSummaryRepository extends JpaRepository<InternalStatementSummary, Long> {

    List<InternalStatementSummary> findByCompanyIdAndYearMonth(Long companyId, String yearMonth);

    Optional<InternalStatementSummary> findByCompanyIdAndEmployeeIdAndYearMonth(
            Long companyId, Long employeeId, String yearMonth);
}
