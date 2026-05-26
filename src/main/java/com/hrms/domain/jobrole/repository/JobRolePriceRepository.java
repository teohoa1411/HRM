package com.hrms.domain.jobrole.repository;

import com.hrms.domain.jobrole.entity.JobRolePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRolePriceRepository extends JpaRepository<JobRolePrice, Long> {

    List<JobRolePrice> findByCompanyIdAndJobRoleIdOrderByEffectiveFromDesc(Long companyId, Long jobRoleId);

    @Query("""
        SELECT p FROM JobRolePrice p
        WHERE p.companyId = :companyId
        AND p.jobRoleId = :jobRoleId
        AND p.effectiveFrom <= :date
        AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
        ORDER BY p.effectiveFrom DESC
        """)
    Optional<JobRolePrice> findActivePrice(
            @Param("companyId") Long companyId,
            @Param("jobRoleId") Long jobRoleId,
            @Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE JobRolePrice p SET p.effectiveTo = :endDate WHERE p.companyId = :companyId AND p.jobRoleId = :jobRoleId AND p.effectiveTo IS NULL")
    void closeCurrentPrice(@Param("companyId") Long companyId, @Param("jobRoleId") Long jobRoleId, @Param("endDate") LocalDate endDate);
}
