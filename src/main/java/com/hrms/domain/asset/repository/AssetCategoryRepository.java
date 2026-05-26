package com.hrms.domain.asset.repository;

import com.hrms.domain.asset.entity.AssetCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

    Page<AssetCategory> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    List<AssetCategory> findByCompanyIdAndActiveIsTrueAndDeletedAtIsNull(Long companyId);

    Optional<AssetCategory> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);

    @Query("""
        SELECT COUNT(a) FROM Asset a
        WHERE a.categoryId = :categoryId
        AND a.deletedAt IS NULL
        """)
    long countAssetsByCategory(@Param("categoryId") Long categoryId);
}
