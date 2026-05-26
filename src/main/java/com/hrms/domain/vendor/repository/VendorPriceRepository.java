package com.hrms.domain.vendor.repository;

import com.hrms.domain.vendor.entity.VendorPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VendorPriceRepository extends JpaRepository<VendorPrice, Long> {

    List<VendorPrice> findByVendorIdOrderByEffectiveFromDesc(Long vendorId);

    List<VendorPrice> findByVendorIdAndJobRoleIdOrderByEffectiveFromDesc(Long vendorId, Long jobRoleId);

    @Query("SELECT p FROM VendorPrice p WHERE p.vendorId = :vendorId AND p.jobRoleId = :jobRoleId AND p.effectiveTo IS NULL")
    Optional<VendorPrice> findActivePrice(Long vendorId, Long jobRoleId);

    @Modifying
    @Query("UPDATE VendorPrice p SET p.effectiveTo = :closedDate WHERE p.vendorId = :vendorId AND p.jobRoleId = :jobRoleId AND p.effectiveTo IS NULL")
    void closeCurrentPrice(Long vendorId, Long jobRoleId, LocalDate closedDate);

    @Query("""
        SELECT p FROM VendorPrice p
        WHERE p.vendorId = :vendorId
          AND p.jobRoleId = :jobRoleId
          AND p.effectiveFrom <= :refDate
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :refDate)
        ORDER BY p.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<VendorPrice> findPriceAt(Long vendorId, Long jobRoleId, LocalDate refDate);
}
