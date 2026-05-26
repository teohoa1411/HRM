package com.hrms.domain.asset.repository;

import com.hrms.domain.asset.entity.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {

    List<AssetAssignment> findByAssetIdAndCompanyIdOrderByAssignedDateDesc(Long assetId, Long companyId);

    List<AssetAssignment> findByEmployeeIdAndReturnedDateIsNullAndCompanyId(Long employeeId, Long companyId);

    Optional<AssetAssignment> findByAssetIdAndReturnedDateIsNull(Long assetId);

    List<AssetAssignment> findByEmployeeIdAndCompanyIdOrderByAssignedDateDesc(Long employeeId, Long companyId);

    @Query("""
        SELECT aa FROM AssetAssignment aa
        WHERE aa.assetId = :assetId
        AND aa.companyId = :companyId
        ORDER BY aa.assignedDate DESC
        """)
    List<AssetAssignment> findAssignmentHistoryByAsset(
            @Param("assetId") Long assetId,
            @Param("companyId") Long companyId);
}
