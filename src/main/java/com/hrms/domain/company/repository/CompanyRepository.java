package com.hrms.domain.company.repository;

import com.hrms.domain.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndDeletedAtIsNull(Long id);

    Optional<Company> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByTaxCodeAndDeletedAtIsNull(String taxCode);

    Page<Company> findByDeletedAtIsNull(Pageable pageable);

    Page<Company> findByActiveAndDeletedAtIsNull(boolean active, Pageable pageable);
}
