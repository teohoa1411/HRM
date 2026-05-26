package com.hrms.domain.insurance.repository;

import com.hrms.domain.common.enums.DeclarationStatus;
import com.hrms.domain.common.enums.DeclarationType;
import com.hrms.domain.insurance.entity.InsuranceDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceDeclarationRepository extends JpaRepository<InsuranceDeclaration, Long> {

    List<InsuranceDeclaration> findByCompanyIdAndDeletedAtIsNullOrderByDeadlineAsc(Long companyId);

    Optional<InsuranceDeclaration> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    List<InsuranceDeclaration> findByCompanyIdAndEffectiveMonthAndDeletedAtIsNull(
            Long companyId, String effectiveMonth);

    List<InsuranceDeclaration> findByCompanyIdAndStatusAndDeletedAtIsNullOrderByDeadlineAsc(
            Long companyId, DeclarationStatus status);

    List<InsuranceDeclaration> findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByEffectiveMonthDesc(
            Long employeeId, Long companyId);

    /** Kiểm tra nhân sự đã có khai báo PENDING/SUBMITTED cho cùng tháng chưa */
    @Query("""
        SELECT COUNT(d) FROM InsuranceDeclaration d
        WHERE d.companyId = :companyId
        AND d.employeeId = :employeeId
        AND d.effectiveMonth = :effectiveMonth
        AND d.status IN ('PENDING', 'SUBMITTED')
        AND d.deletedAt IS NULL
        """)
    long countActiveByEmployeeAndMonth(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("effectiveMonth") String effectiveMonth);

    /** Tìm declarations sắp tới deadline */
    @Query("""
        SELECT d FROM InsuranceDeclaration d
        WHERE d.companyId = :companyId
        AND d.status = 'PENDING'
        AND d.deadline BETWEEN :from AND :to
        AND d.deletedAt IS NULL
        ORDER BY d.deadline ASC
        """)
    List<InsuranceDeclaration> findUpcomingDeadlines(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Tìm declarations quá hạn */
    @Query("""
        SELECT d FROM InsuranceDeclaration d
        WHERE d.companyId = :companyId
        AND d.status = 'PENDING'
        AND d.deadline < :today
        AND d.deletedAt IS NULL
        ORDER BY d.deadline ASC
        """)
    List<InsuranceDeclaration> findOverdue(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today);
}
