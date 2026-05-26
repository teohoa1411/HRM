package com.hrms.domain.contract.repository;

import com.hrms.domain.common.enums.ContractStatus;
import com.hrms.domain.contract.entity.EmployeeContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeContractRepository extends JpaRepository<EmployeeContract, Long> {

    Page<EmployeeContract> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Optional<EmployeeContract> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    Optional<EmployeeContract> findByPublicIdAndCompanyIdAndDeletedAtIsNull(String publicId, Long companyId);

    List<EmployeeContract> findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByEffectiveFromDesc(
            Long employeeId, Long companyId);

    /** Tìm HĐ sắp hết hạn trong khoảng [today, today + days] */
    @Query("""
        SELECT c FROM EmployeeContract c
        WHERE c.companyId = :companyId
        AND c.status = 'ACTIVE'
        AND c.effectiveTo IS NOT NULL
        AND c.effectiveTo BETWEEN :from AND :to
        AND c.deletedAt IS NULL
        ORDER BY c.effectiveTo ASC
        """)
    List<EmployeeContract> findExpiringSoon(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Tìm HĐ đã quá hạn nhưng chưa được đánh dấu EXPIRED */
    @Query("""
        SELECT c FROM EmployeeContract c
        WHERE c.companyId = :companyId
        AND c.status = 'ACTIVE'
        AND c.effectiveTo IS NOT NULL
        AND c.effectiveTo < :today
        AND c.deletedAt IS NULL
        """)
    List<EmployeeContract> findOverdue(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today);

    /** Đếm HĐ ACTIVE của 1 nhân viên (kiểm tra nhân viên chưa có HĐ) */
    long countByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
            Long employeeId, Long companyId, ContractStatus status);

    /** Filter theo contractTypeId */
    Page<EmployeeContract> findByCompanyIdAndContractTypeIdAndDeletedAtIsNull(
            Long companyId, Long contractTypeId, Pageable pageable);
}
