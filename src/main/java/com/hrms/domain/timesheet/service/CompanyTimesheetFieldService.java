package com.hrms.domain.timesheet.service;

import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.timesheet.dto.CompanyTimesheetFieldRequest;
import com.hrms.domain.timesheet.dto.CompanyTimesheetFieldResponse;
import com.hrms.domain.timesheet.entity.CompanyTimesheetField;
import com.hrms.domain.timesheet.repository.CompanyTimesheetFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyTimesheetFieldService {

    private final CompanyTimesheetFieldRepository repo;

    public List<CompanyTimesheetFieldResponse> listAll(Long companyId) {
        return repo.findByCompanyIdOrderBySortOrderAsc(companyId)
                   .stream().map(CompanyTimesheetFieldResponse::from).toList();
    }

    public List<CompanyTimesheetFieldResponse> listActive(Long companyId) {
        return repo.findByCompanyIdAndActiveTrueOrderBySortOrderAsc(companyId)
                   .stream().map(CompanyTimesheetFieldResponse::from).toList();
    }

    @Transactional
    public CompanyTimesheetFieldResponse create(Long companyId, CompanyTimesheetFieldRequest req) {
        if (repo.existsByCompanyIdAndFieldKey(companyId, req.fieldKey()))
            throw new BusinessException("Field key already exists: " + req.fieldKey(), HttpStatus.CONFLICT);

        CompanyTimesheetField f = CompanyTimesheetField.builder()
                .companyId(companyId)
                .fieldName(req.fieldName())
                .fieldKey(req.fieldKey())
                .fieldType(req.fieldType() != null ? req.fieldType() : "text")
                .required(req.required())
                .sortOrder(req.sortOrder())
                .build();
        return CompanyTimesheetFieldResponse.from(repo.save(f));
    }

    @Transactional
    public CompanyTimesheetFieldResponse update(Long id, Long companyId, CompanyTimesheetFieldRequest req) {
        CompanyTimesheetField f = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Field not found"));
        f.setFieldName(req.fieldName());
        f.setFieldType(req.fieldType() != null ? req.fieldType() : f.getFieldType());
        f.setRequired(req.required());
        f.setSortOrder(req.sortOrder());
        return CompanyTimesheetFieldResponse.from(repo.save(f));
    }

    @Transactional
    public void toggleActive(Long id, Long companyId) {
        CompanyTimesheetField f = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Field not found"));
        f.setActive(!f.isActive());
        repo.save(f);
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        CompanyTimesheetField f = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Field not found"));
        repo.delete(f);
    }
}
