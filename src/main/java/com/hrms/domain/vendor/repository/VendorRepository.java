package com.hrms.domain.vendor.repository;

import com.hrms.domain.vendor.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Page<Vendor> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Optional<Vendor> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);
}
