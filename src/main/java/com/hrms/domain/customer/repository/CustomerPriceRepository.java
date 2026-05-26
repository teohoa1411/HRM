package com.hrms.domain.customer.repository;

import com.hrms.domain.customer.entity.CustomerPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    List<CustomerPrice> findByCustomerIdAndJobRoleIdOrderByEffectiveFromDesc(Long customerId, Long jobRoleId);

    List<CustomerPrice> findByCustomerIdOrderByEffectiveFromDesc(Long customerId);

    @Query("SELECT p FROM CustomerPrice p WHERE p.customerId = :customerId AND p.jobRoleId = :jobRoleId AND p.effectiveTo IS NULL")
    Optional<CustomerPrice> findActivePrice(Long customerId, Long jobRoleId);

    @Modifying
    @Query("UPDATE CustomerPrice p SET p.effectiveTo = :closedDate WHERE p.customerId = :customerId AND p.jobRoleId = :jobRoleId AND p.effectiveTo IS NULL")
    void closeCurrentPrice(Long customerId, Long jobRoleId, LocalDate closedDate);

    @Query("""
        SELECT p FROM CustomerPrice p
        WHERE p.customerId = :customerId
          AND p.jobRoleId = :jobRoleId
          AND p.effectiveFrom <= :refDate
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :refDate)
        ORDER BY p.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<CustomerPrice> findPriceAt(Long customerId, Long jobRoleId, LocalDate refDate);
}
