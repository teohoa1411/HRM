package com.hrms.domain.jobrole.service;

import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.jobrole.dto.CreateJobRoleRequest;
import com.hrms.domain.jobrole.dto.JobRoleResponse;
import com.hrms.domain.jobrole.dto.SetJobRolePriceRequest;
import com.hrms.domain.jobrole.entity.JobRole;
import com.hrms.domain.jobrole.entity.JobRolePrice;
import com.hrms.domain.jobrole.mapper.JobRoleMapper;
import com.hrms.domain.jobrole.repository.JobRolePriceRepository;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobRoleService {

    private final JobRoleRepository jobRoleRepository;
    private final JobRolePriceRepository jobRolePriceRepository;
    private final JobRoleMapper jobRoleMapper;

    @Transactional(readOnly = true)
    public Page<JobRoleResponse> listAll(Long companyId, Pageable pageable) {
        return jobRoleRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
                .map(jobRoleMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<JobRoleResponse> listActive(Long companyId) {
        return jobRoleRepository.findByCompanyIdAndActiveAndDeletedAtIsNull(companyId, true).stream()
                .map(jobRoleMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobRoleResponse getById(Long id, Long companyId) {
        return jobRoleMapper.toResponse(
                jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, id)
                        .orElseThrow(() -> new NotFoundException("JobRole", id)));
    }

    @Transactional
    public JobRoleResponse createJobRole(Long companyId, CreateJobRoleRequest request) {
        if (jobRoleRepository.existsByCompanyIdAndCodeAndDeletedAtIsNull(companyId, request.code())) {
            throw new DuplicateResourceException("Job role code '" + request.code() + "' đã tồn tại trong công ty");
        }

        JobRole jobRole = JobRole.builder()
                .companyId(companyId)
                .code(request.code().toUpperCase().trim())
                .name(request.name())
                .category(request.category())
                .seniorityLevel(request.seniorityLevel() != null ? request.seniorityLevel() : "MIDDLE")
                .description(request.description())
                .skillsRequired(toValidJson(request.skillsRequired()))
                .active(true)
                .build();

        return jobRoleMapper.toResponse(jobRoleRepository.save(jobRole));
    }

    @Transactional
    public JobRoleResponse updateJobRole(Long id, Long companyId, CreateJobRoleRequest request) {
        JobRole jobRole = jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, id)
                .orElseThrow(() -> new NotFoundException("JobRole", id));

        jobRole.setName(request.name());
        jobRole.setCategory(request.category());
        jobRole.setSeniorityLevel(request.seniorityLevel() != null ? request.seniorityLevel() : jobRole.getSeniorityLevel());
        jobRole.setDescription(request.description());
        jobRole.setSkillsRequired(toValidJson(request.skillsRequired()));

        return jobRoleMapper.toResponse(jobRoleRepository.save(jobRole));
    }

    @Transactional
    public void toggleActive(Long id, Long companyId) {
        JobRole jobRole = jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, id)
                .orElseThrow(() -> new NotFoundException("JobRole", id));
        jobRole.setActive(!jobRole.isActive());
        jobRoleRepository.save(jobRole);
    }

    @Transactional
    public void setPrice(Long jobRoleId, Long companyId, SetJobRolePriceRequest request) {
        // Validate job role thuộc công ty này
        jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, jobRoleId)
                .orElseThrow(() -> new NotFoundException("JobRole", jobRoleId));

        // Close current active price
        jobRolePriceRepository.closeCurrentPrice(companyId, jobRoleId,
                request.effectiveFrom().minusDays(1));

        // Create new price record (immutable)
        JobRolePrice newPrice = JobRolePrice.builder()
                .companyId(companyId)
                .jobRoleId(jobRoleId)
                .costPrice(request.costPrice())
                .sellPrice(request.sellPrice())
                .currency(request.currency() != null ? request.currency() : "VND")
                .billingUnit(request.billingUnit() != null ? request.billingUnit() : "MONTHLY")
                .effectiveFrom(request.effectiveFrom())
                .notes(request.notes())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        jobRolePriceRepository.save(newPrice);
    }

    @Transactional(readOnly = true)
    public List<JobRolePrice> getPriceHistory(Long jobRoleId, Long companyId) {
        return jobRolePriceRepository.findByCompanyIdAndJobRoleIdOrderByEffectiveFromDesc(companyId, jobRoleId);
    }

    @Transactional
    public void deleteJobRole(Long id, Long companyId) {
        JobRole jobRole = jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, id)
                .orElseThrow(() -> new NotFoundException("JobRole", id));
        jobRole.softDelete();
        jobRoleRepository.save(jobRole);
    }

    /**
     * Convert plain string to valid JSON string for the skills_required JSON column.
     * If already valid JSON (array/object/quoted), keep as-is.
     */
    private String toValidJson(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{") || trimmed.startsWith("\"")) {
            return trimmed;
        }
        // Escape and wrap as JSON string
        return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Validate job role tồn tại và thuộc công ty — dùng từ EmployeeService, CustomerService.
     */
    public void assertBelongsToCompany(Long jobRoleId, Long companyId) {
        if (!jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, jobRoleId).isPresent()) {
            throw new BusinessException(
                    "Job role không tồn tại hoặc không thuộc công ty này",
                    HttpStatus.BAD_REQUEST, "JOBROLE_NOT_FOUND");
        }
    }
}
