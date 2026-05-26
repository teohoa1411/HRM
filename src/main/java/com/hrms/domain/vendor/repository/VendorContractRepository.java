package com.hrms.domain.vendor.repository;

import com.hrms.domain.vendor.entity.VendorContract;
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
public interface VendorContractRepository extends JpaRepository<VendorContract, Long> {

    Page<VendorContract> findByVendorIdAndCompanyIdAndDeletedAtIsNull(Long vendorId, Long companyId, Pageable pageable);

    Optional<VendorContract> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    @Query("""
        SELECT c FROM VendorContract c
        WHERE c.companyId = :companyId
        AND c.status = 'ACTIVE'
        AND c.endDate IS NOT NULL
        AND c.endDate <= :reminderDate
        AND c.deletedAt IS NULL
        """)
    List<VendorContract> findExpiringContracts(@Param("companyId") Long companyId, @Param("reminderDate") LocalDate reminderDate);
}
