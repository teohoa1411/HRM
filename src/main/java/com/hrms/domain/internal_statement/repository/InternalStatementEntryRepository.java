package com.hrms.domain.internal_statement.repository;

import com.hrms.domain.internal_statement.entity.InternalStatementEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InternalStatementEntryRepository extends JpaRepository<InternalStatementEntry, Long> {

    List<InternalStatementEntry> findByCompanyIdAndEmployeeIdAndYearMonth(
            Long companyId, Long employeeId, String yearMonth);

    List<InternalStatementEntry> findByCompanyIdAndYearMonth(Long companyId, String yearMonth);

    Optional<InternalStatementEntry> findByCompanyIdAndEmployeeIdAndWorkDate(
            Long companyId, Long employeeId, LocalDate workDate);

    @Modifying
    @Query("DELETE FROM InternalStatementEntry e WHERE e.companyId = :companyId AND e.employeeId = :employeeId AND e.yearMonth = :yearMonth")
    void deleteByCompanyIdAndEmployeeIdAndYearMonth(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("yearMonth") String yearMonth);
}
