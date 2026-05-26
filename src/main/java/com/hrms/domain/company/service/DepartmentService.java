package com.hrms.domain.company.service;

import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.company.dto.DepartmentRequest;
import com.hrms.domain.company.dto.DepartmentResponse;
import com.hrms.domain.company.entity.Department;
import com.hrms.domain.company.mapper.CompanyMapper;
import com.hrms.domain.company.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyMapper companyMapper;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list(Long companyId) {
        return departmentRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().map(companyMapper::toDepartmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id, Long companyId) {
        return companyMapper.toDepartmentResponse(
                departmentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                        .orElseThrow(() -> new NotFoundException("Department", id)));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request, Long companyId) {
        if (departmentRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.code(), companyId)) {
            throw new DuplicateResourceException("Department code already exists: " + request.code());
        }
        Department dept = Department.builder()
                .companyId(companyId)
                .code(request.code())
                .name(request.name())
                .parentDeptId(request.parentDeptId())
                .managerEmployeeId(request.managerEmployeeId())
                .description(request.description())
                .displayOrder(request.displayOrder())
                .active(request.active() != null ? request.active() : true)
                .build();
        return companyMapper.toDepartmentResponse(departmentRepository.save(dept));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request, Long companyId) {
        Department dept = departmentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Department", id));
        dept.setName(request.name());
        dept.setParentDeptId(request.parentDeptId());
        dept.setManagerEmployeeId(request.managerEmployeeId());
        dept.setDescription(request.description());
        dept.setDisplayOrder(request.displayOrder());
        if (request.active() != null) dept.setActive(request.active());
        return companyMapper.toDepartmentResponse(departmentRepository.save(dept));
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        Department dept = departmentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Department", id));
        dept.softDelete();
        departmentRepository.save(dept);
    }
}
