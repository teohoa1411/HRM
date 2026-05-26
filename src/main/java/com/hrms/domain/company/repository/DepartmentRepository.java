package com.hrms.domain.company.repository;

import com.hrms.domain.company.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    Optional<Department> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);
}
