package com.hrms.domain.timesheet.repository;

import com.hrms.domain.timesheet.entity.CompanyTimesheetField;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyTimesheetFieldRepository extends JpaRepository<CompanyTimesheetField, Long> {
    List<CompanyTimesheetField> findByCompanyIdAndActiveTrueOrderBySortOrderAsc(Long companyId);
    List<CompanyTimesheetField> findByCompanyIdAndActiveTrueOrderByIdAsc(Long companyId);
    List<CompanyTimesheetField> findByCompanyIdOrderBySortOrderAsc(Long companyId);
    Optional<CompanyTimesheetField> findByIdAndCompanyId(Long id, Long companyId);
    boolean existsByCompanyIdAndFieldKey(Long companyId, String fieldKey);
}
