package com.hrms.domain.insurance.repository;

import com.hrms.domain.insurance.entity.InsuranceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsuranceHistoryRepository extends JpaRepository<InsuranceHistory, Long> {

    List<InsuranceHistory> findByInsurancePolicyIdOrderByChangedAtDesc(Long insurancePolicyId);
}
