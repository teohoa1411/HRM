package com.hrms.domain.insurance.repository;

import com.hrms.domain.common.enums.InsurancePolicyStatus;
import com.hrms.domain.insurance.entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByCompanyIdAndDeletedAtIsNullOrderByEmployeeIdAsc(Long companyId);

    List<InsurancePolicy> findByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, InsurancePolicyStatus status);

    Optional<InsurancePolicy> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    Optional<InsurancePolicy> findByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
            Long employeeId, Long companyId, InsurancePolicyStatus status);

    List<InsurancePolicy> findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByStartDateDesc(
            Long employeeId, Long companyId);

    boolean existsByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
            Long employeeId, Long companyId, InsurancePolicyStatus status);
}
