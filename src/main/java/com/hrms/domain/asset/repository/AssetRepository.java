package com.hrms.domain.asset.repository;

import com.hrms.domain.asset.entity.Asset;
import com.hrms.domain.common.enums.AssetOwnershipType;
import com.hrms.domain.common.enums.AssetStatus;
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
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Page<Asset> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Optional<Asset> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByAssetCodeAndCompanyIdAndDeletedAtIsNull(String assetCode, Long companyId);

    long countByCompanyId(Long companyId);

    @Query("""
        SELECT a FROM Asset a
        WHERE a.companyId = :companyId
        AND a.deletedAt IS NULL
        AND (:status IS NULL OR a.status = :status)
        AND (:categoryId IS NULL OR a.categoryId = :categoryId)
        AND (:ownershipType IS NULL OR a.ownershipType = :ownershipType)
        """)
    Page<Asset> findByCompanyIdWithFilters(
            @Param("companyId") Long companyId,
            @Param("status") AssetStatus status,
            @Param("categoryId") Long categoryId,
            @Param("ownershipType") AssetOwnershipType ownershipType,
            Pageable pageable);

    @Query(value = """
        SELECT * FROM assets a
        WHERE a.company_id = :companyId
        AND a.deleted_at IS NULL
        AND a.purchase_date IS NOT NULL
        AND a.warranty_months IS NOT NULL
        AND DATE_ADD(a.purchase_date, INTERVAL a.warranty_months MONTH)
            BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)
        """, nativeQuery = true)
    List<Asset> findWarrantyAlertsForCompany(@Param("companyId") Long companyId);

    @Query("""
        SELECT COUNT(a) FROM Asset a
        WHERE a.companyId = :companyId
        AND a.deletedAt IS NULL
        """)
    long countTotalByCompanyId(@Param("companyId") Long companyId);

    long countByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, AssetStatus status);

    long countByCompanyIdAndRentedIsTrueAndDeletedAtIsNull(Long companyId);

    @Query(value = """
        SELECT a.* FROM assets a
        INNER JOIN asset_assignments aa ON aa.asset_id = a.id AND aa.returned_date IS NULL
        INNER JOIN employees e ON e.id = aa.employee_id
        WHERE a.company_id = :companyId AND a.deleted_at IS NULL
        AND e.source_type = :sourceType
        AND (:vendorId IS NULL OR e.vendor_id = :vendorId)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT a.id) FROM assets a
        INNER JOIN asset_assignments aa ON aa.asset_id = a.id AND aa.returned_date IS NULL
        INNER JOIN employees e ON e.id = aa.employee_id
        WHERE a.company_id = :companyId AND a.deleted_at IS NULL
        AND e.source_type = :sourceType
        AND (:vendorId IS NULL OR e.vendor_id = :vendorId)
        """,
        nativeQuery = true)
    Page<Asset> findByUsedByFilter(
            @Param("companyId") Long companyId,
            @Param("sourceType") String sourceType,
            @Param("vendorId") Long vendorId,
            Pageable pageable);

    @Query(value = """
        SELECT COUNT(DISTINCT a.id) FROM assets a
        INNER JOIN asset_assignments aa ON aa.asset_id = a.id AND aa.returned_date IS NULL
        INNER JOIN employees e ON e.id = aa.employee_id AND e.source_type = 'VENDOR'
        WHERE a.company_id = :companyId AND a.deleted_at IS NULL
        """, nativeQuery = true)
    long countAssignedToVendorEmployees(@Param("companyId") Long companyId);
}