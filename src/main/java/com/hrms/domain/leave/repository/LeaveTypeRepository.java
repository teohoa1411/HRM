package com.hrms.domain.leave.repository;

import com.hrms.domain.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    List<LeaveType> findByCompanyIdAndActiveTrue(Long companyId);
    List<LeaveType> findByCompanyId(Long companyId);
    Optional<LeaveType> findByIdAndCompanyId(Long id, Long companyId);
    boolean existsByCodeAndCompanyId(String code, Long companyId);
}
