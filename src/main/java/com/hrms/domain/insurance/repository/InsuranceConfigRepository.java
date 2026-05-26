package com.hrms.domain.insurance.repository;

import com.hrms.domain.common.enums.InsuranceType;
import com.hrms.domain.insurance.entity.InsuranceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceConfigRepository extends JpaRepository<InsuranceConfig, Long> {

    List<InsuranceConfig> findByCompanyIdOrderByInsuranceTypeAscEffectiveFromDesc(Long companyId);

    Optional<InsuranceConfig> findByIdAndCompanyId(Long id, Long companyId);

    /** Lấy config đang có hiệu lực cho loại BH tại ngày chỉ định */
    @Query("""
        SELECT c FROM InsuranceConfig c
        WHERE c.companyId = :companyId
        AND c.insuranceType = :type
        AND c.effectiveFrom <= :date
        AND (c.effectiveTo IS NULL OR c.effectiveTo >= :date)
        ORDER BY c.effectiveFrom DESC
        """)
    Optional<InsuranceConfig> findActiveConfig(
            @Param("companyId") Long companyId,
            @Param("type") InsuranceType type,
            @Param("date") LocalDate date);

    /** Lấy tất cả config đang có hiệu lực tại ngày chỉ định */
    @Query("""
        SELECT c FROM InsuranceConfig c
        WHERE c.companyId = :companyId
        AND c.effectiveFrom <= :date
        AND (c.effectiveTo IS NULL OR c.effectiveTo >= :date)
        ORDER BY c.insuranceType ASC
        """)
    List<InsuranceConfig> findAllActiveConfigs(
            @Param("companyId") Long companyId,
            @Param("date") LocalDate date);

    /**
     * Fallback: lấy config mới nhất theo từng loại BH khi không có config
     * có hiệu lực tại ngày chỉ định (vd: config được tạo sau tháng tính lương).
     * Lấy 1 config mới nhất PER type → dùng LIMIT 1 per type qua subquery.
     */
    @Query("""
        SELECT c FROM InsuranceConfig c
        WHERE c.companyId = :companyId
        AND c.effectiveTo IS NULL
        ORDER BY c.insuranceType ASC, c.effectiveFrom DESC
        """)
    List<InsuranceConfig> findLatestOpenConfigs(@Param("companyId") Long companyId);
}
