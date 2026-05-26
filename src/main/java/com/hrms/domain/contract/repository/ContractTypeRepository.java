package com.hrms.domain.contract.repository;

import com.hrms.domain.common.enums.ContractCategory;
import com.hrms.domain.contract.entity.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {

    List<ContractType> findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(Long companyId);

    List<ContractType> findByCompanyIdOrderBySortOrderAscNameAsc(Long companyId);

    List<ContractType> findByCompanyIdAndCategoryAndActiveTrueOrderBySortOrderAsc(Long companyId, ContractCategory category);

    Optional<ContractType> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCodeAndCompanyId(String code, Long companyId);
}
