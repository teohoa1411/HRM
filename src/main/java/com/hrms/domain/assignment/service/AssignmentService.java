package com.hrms.domain.assignment.service;

import com.hrms.domain.assignment.dto.AssignmentRequest;
import com.hrms.domain.assignment.dto.AssignmentResponse;
import com.hrms.domain.assignment.entity.EmployeeAssignment;
import com.hrms.domain.assignment.repository.EmployeeAssignmentRepository;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.customer.repository.CustomerRepository;
import com.hrms.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final EmployeeAssignmentRepository assignmentRepo;
    private final EmployeeRepository employeeRepo;
    private final CustomerRepository customerRepo;

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getByEmployee(Long employeeId, Long companyId) {
        employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        return assignmentRepo.findByEmployeeIdAndDeletedAtIsNull(employeeId)
                .stream().map(a -> toResponse(a, companyId)).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getByCompany(Long companyId) {
        return assignmentRepo.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().map(a -> toResponse(a, companyId)).toList();
    }

    @Transactional
    public AssignmentResponse create(Long employeeId, Long companyId, AssignmentRequest req) {
        employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        customerRepo.findByIdAndCompanyIdAndDeletedAtIsNull(req.customerId(), companyId)
                .orElseThrow(() -> new NotFoundException("Customer", req.customerId()));
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException("End date must be after start date");
        }

        // Rule: 1 NV / 1 ngày = tối đa 1 customer → reject nếu có overlap.
        assertNoOverlap(employeeId, req.startDate(), req.endDate(), 0L);

        EmployeeAssignment a = EmployeeAssignment.builder()
                .companyId(companyId).employeeId(employeeId)
                .customerId(req.customerId()).projectName(req.projectName())
                .projectCode(req.projectCode()).startDate(req.startDate())
                .endDate(req.endDate()).notes(req.notes())
                .clientUsername(req.clientUsername()).workLocation(req.workLocation()).build();
        return toResponse(assignmentRepo.save(a), companyId);
    }

    @Transactional
    public AssignmentResponse update(Long id, Long companyId, AssignmentRequest req) {
        EmployeeAssignment a = assignmentRepo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Assignment", id));
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException("End date must be after start date");
        }
        // Loại trừ chính nó khỏi overlap check
        assertNoOverlap(a.getEmployeeId(), req.startDate(), req.endDate(), a.getId());

        a.setCustomerId(req.customerId());
        a.setProjectName(req.projectName());
        a.setProjectCode(req.projectCode());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setNotes(req.notes());
        a.setClientUsername(req.clientUsername());
        a.setWorkLocation(req.workLocation());
        return toResponse(assignmentRepo.save(a), companyId);
    }

    /**
     * Đảm bảo không có assignment nào khác của cùng nhân sự overlap với khoảng đã cho.
     * Đây là tầng bảo vệ duy nhất chống overlap (MySQL không hỗ trợ EXCLUDE constraint
     * như PostgreSQL), nên mọi entry-point ghi assignment đều phải đi qua method này.
     */
    private void assertNoOverlap(Long employeeId, LocalDate startDate, LocalDate endDate, Long excludeId) {
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(9999, 12, 31);
        boolean overlap = assignmentRepo.existsOverlap(employeeId, startDate, effectiveEnd, excludeId);
        if (overlap) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String range = endDate != null
                    ? startDate.format(fmt) + " - " + endDate.format(fmt)
                    : "từ " + startDate.format(fmt);
            throw new BusinessException(
                    "Nhân viên đã có phân công khác trong khoảng " + range
                            + ". Mỗi nhân viên chỉ được phân công 1 dự án trong cùng thời điểm.");
        }
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        EmployeeAssignment a = assignmentRepo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Assignment", id));
        a.softDelete();
        assignmentRepo.save(a);
    }

    private AssignmentResponse toResponse(EmployeeAssignment a, Long companyId) {
        String empName = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(a.getEmployeeId(), companyId)
                .map(e -> e.getFullName()).orElse("Unknown");
        String custName = customerRepo.findByIdAndCompanyIdAndDeletedAtIsNull(a.getCustomerId(), companyId)
                .map(c -> c.getName()).orElse("Unknown");
        return new AssignmentResponse(a.getId(), a.getCompanyId(), a.getEmployeeId(), empName,
                a.getCustomerId(), custName, a.getProjectName(), a.getProjectCode(),
                a.getStartDate(), a.getEndDate(), a.getNotes(),
                a.getClientUsername(), a.getWorkLocation(), a.getCreatedAt());
    }
}
