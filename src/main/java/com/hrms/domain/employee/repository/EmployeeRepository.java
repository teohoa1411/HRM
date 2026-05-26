package com.hrms.domain.employee.repository;

import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.EmployeeStatus;
import com.hrms.domain.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Page<Employee> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    List<Employee> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    Page<Employee> findByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, EmployeeStatus status, Pageable pageable);

    Page<Employee> findByCompanyIdAndSourceTypeAndDeletedAtIsNull(Long companyId, EmployeeSourceType sourceType, Pageable pageable);

    Optional<Employee> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    Optional<Employee> findByIdAndDeletedAtIsNull(Long id);

    Optional<Employee> findByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Employee> findByPublicIdAndCompanyIdAndDeletedAtIsNull(String publicId, Long companyId);

    Optional<Employee> findByPublicIdAndDeletedAtIsNull(String publicId);

    boolean existsByEmployeeCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);

    // Count ALL employees for this company (including soft-deleted) — used for code generation
    long countByCompanyId(Long companyId);

    @Query("""
        SELECT COUNT(e) FROM Employee e
        WHERE e.companyId = :companyId
        AND e.status = 'ACTIVE'
        AND e.deletedAt IS NULL
        """)
    long countActiveByCompanyId(@Param("companyId") Long companyId);

    @Query("""
        SELECT COUNT(e) FROM Employee e
        WHERE e.companyId = :companyId
        AND e.sourceType = :sourceType
        AND e.status = 'ACTIVE'
        AND e.deletedAt IS NULL
        """)
    long countActiveByCompanyIdAndSourceType(@Param("companyId") Long companyId, @Param("sourceType") EmployeeSourceType sourceType);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.companyId = :companyId
        AND e.deletedAt IS NULL
        AND (LOWER(e.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
             OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :term, '%')))
        ORDER BY e.fullName
        """)
    List<Employee> searchByNameOrCode(@Param("companyId") Long companyId, @Param("term") String term);
}
