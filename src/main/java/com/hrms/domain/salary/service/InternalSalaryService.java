package com.hrms.domain.salary.service;

import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.salary.dto.SalaryResponse;
import com.hrms.domain.salary.dto.SetSalaryRequest;
import com.hrms.domain.salary.entity.InternalSalary;
import com.hrms.domain.salary.repository.InternalSalaryRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalSalaryService {

    private final InternalSalaryRepository salaryRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public void setSalary(Long employeeId, Long companyId, SetSalaryRequest request) {
        employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));

        salaryRepository.closeCurrentSalary(employeeId, request.effectiveFrom().minusDays(1));

        InternalSalary salary = InternalSalary.builder()
                .companyId(companyId)
                .employeeId(employeeId)
                .monthlySalary(request.monthlySalary())
                .effectiveFrom(request.effectiveFrom())
                .notes(request.notes())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        salaryRepository.save(salary);
    }

    @Transactional(readOnly = true)
    public List<SalaryResponse> getHistory(Long employeeId, Long companyId) {
        employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        return salaryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)
                .stream()
                .map(s -> new SalaryResponse(s.getId(), s.getEmployeeId(), s.getMonthlySalary(),
                        s.getEffectiveFrom(), s.getEffectiveTo(), s.getNotes(), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaryResponse getCurrent(Long employeeId, Long companyId) {
        employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        return salaryRepository.findCurrentSalary(employeeId)
                .map(s -> new SalaryResponse(s.getId(), s.getEmployeeId(), s.getMonthlySalary(),
                        s.getEffectiveFrom(), s.getEffectiveTo(), s.getNotes(), s.getCreatedAt()))
                .orElse(null);
    }
}
