package com.hrms.domain.insurance.service;

import com.hrms.domain.common.enums.DeclarationStatus;
import com.hrms.domain.common.enums.DeclarationType;
import com.hrms.domain.common.enums.InsurancePolicyStatus;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.insurance.dto.InsuranceDeclarationRequest;
import com.hrms.domain.insurance.dto.InsuranceDeclarationResponse;
import com.hrms.domain.insurance.entity.InsuranceDeclaration;
import com.hrms.domain.insurance.entity.InsuranceHistory;
import com.hrms.domain.insurance.entity.InsurancePolicy;
import com.hrms.domain.insurance.repository.InsuranceDeclarationRepository;
import com.hrms.domain.insurance.repository.InsuranceHistoryRepository;
import com.hrms.domain.insurance.repository.InsurancePolicyRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceDeclarationService {

    private final InsuranceDeclarationRepository declarationRepository;
    private final InsurancePolicyRepository policyRepository;
    private final InsuranceHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ==================== CRUD ====================

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getAll(Long companyId) {
        return declarationRepository.findByCompanyIdAndDeletedAtIsNullOrderByDeadlineAsc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getByMonth(Long companyId, String effectiveMonth) {
        return declarationRepository.findByCompanyIdAndEffectiveMonthAndDeletedAtIsNull(companyId, effectiveMonth)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getPending(Long companyId) {
        return declarationRepository.findByCompanyIdAndStatusAndDeletedAtIsNullOrderByDeadlineAsc(
                        companyId, DeclarationStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getByEmployee(Long employeeId, Long companyId) {
        return declarationRepository.findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByEffectiveMonthDesc(
                        employeeId, companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getUpcoming(Long companyId, int days) {
        LocalDate today = LocalDate.now();
        return declarationRepository.findUpcomingDeadlines(companyId, today, today.plusDays(days))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceDeclarationResponse> getOverdue(Long companyId) {
        return declarationRepository.findOverdue(companyId, LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public InsuranceDeclarationResponse create(Long companyId, InsuranceDeclarationRequest request) {
        // Bug 8: Chặn tạo duplicate — 1 NV chỉ có 1 khai báo PENDING/SUBMITTED cho 1 tháng
        assertNoDuplicateDeclaration(companyId, request.employeeId(), request.effectiveMonth());

        InsuranceDeclaration entity = InsuranceDeclaration.builder()
                .companyId(companyId)
                .employeeId(request.employeeId())
                .declarationType(request.declarationType())
                .effectiveMonth(request.effectiveMonth())
                .deadline(request.deadline())
                .status(DeclarationStatus.PENDING)
                .oldSalary(request.oldSalary())
                .newSalary(request.newSalary())
                .reason(request.reason())
                .build();

        return toResponse(declarationRepository.save(entity));
    }

    @Transactional
    public InsuranceDeclarationResponse updateStatus(Long id, Long companyId, DeclarationStatus newStatus) {
        InsuranceDeclaration entity = declarationRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("InsuranceDeclaration", id));

        // Validate FSM: chỉ cho phép chuyển trạng thái hợp lệ
        validateStatusTransition(entity.getStatus(), newStatus);

        entity.setStatus(newStatus);
        entity = declarationRepository.save(entity);

        // Khi APPROVED: tự động cập nhật InsurancePolicy + ghi lịch sử
        if (newStatus == DeclarationStatus.APPROVED && entity.getNewSalary() != null) {
            syncPolicyFromDeclaration(entity, companyId);
        }

        return toResponse(entity);
    }

    /**
     * Khi khai báo được duyệt → cập nhật insuranceSalary trong policy + ghi history.
     */
    private void syncPolicyFromDeclaration(InsuranceDeclaration declaration, Long companyId) {
        InsurancePolicy policy = policyRepository
                .findByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                        declaration.getEmployeeId(), companyId, InsurancePolicyStatus.ACTIVE)
                .orElse(null);

        if (policy == null) {
            log.warn("Declaration #{} approved but no ACTIVE policy found for employee={}. " +
                     "Policy needs to be created manually.",
                    declaration.getId(), declaration.getEmployeeId());
            return;
        }

        String oldSalary = policy.getInsuranceSalary() != null
                ? policy.getInsuranceSalary().toPlainString() : "0";
        String newSalary = declaration.getNewSalary().toPlainString();

        // Cập nhật policy
        policy.setInsuranceSalary(declaration.getNewSalary());
        policyRepository.save(policy);

        // Ghi lịch sử thay đổi
        Long currentUserId = null;
        try { currentUserId = SecurityUtils.getCurrentUserId(); } catch (Exception ignored) {}

        historyRepository.save(InsuranceHistory.builder()
                .insurancePolicyId(policy.getId())
                .changedField("insurance_salary")
                .oldValue(oldSalary)
                .newValue(newSalary)
                .changedBy(currentUserId)
                .build());

        log.info("Declaration #{} APPROVED: employee={} insurance_salary updated {} → {}",
                declaration.getId(), declaration.getEmployeeId(), oldSalary, newSalary);
    }

    /**
     * Validate chuyển trạng thái hợp lệ:
     *   PENDING → SUBMITTED / APPROVED / REJECTED
     *   SUBMITTED → APPROVED / REJECTED
     *   APPROVED / REJECTED → không cho chuyển tiếp
     */
    private void validateStatusTransition(DeclarationStatus current, DeclarationStatus target) {
        if (current == DeclarationStatus.APPROVED) {
            throw new BusinessException("Khai báo đã được duyệt, không thể thay đổi trạng thái.",
                    HttpStatus.BAD_REQUEST, "DECLARATION_ALREADY_APPROVED");
        }
        if (current == DeclarationStatus.REJECTED && target != DeclarationStatus.PENDING) {
            throw new BusinessException("Khai báo đã bị từ chối. Chỉ có thể chuyển về PENDING để xét lại.",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION");
        }
    }

    // ==================== AUTO-CREATE LOGIC ====================

    /**
     * Tự động tạo báo TĂNG khi NV ký HĐ chính thức.
     * Deadline = ngày 1 tháng effective_from - 15 ngày.
     */
    @Transactional
    public void createIncreaseDeclaration(Long companyId, Long employeeId,
                                           LocalDate contractEffectiveFrom, String reason) {
        String effectiveMonth = contractEffectiveFrom.format(MONTH_FMT);

        // Bug 8: Skip nếu đã có khai báo đang xử lý cho tháng này
        if (hasPendingDeclaration(companyId, employeeId, effectiveMonth)) {
            log.info("Skipped auto-create INCREASE for employee={}, month={}: already has pending declaration",
                    employeeId, effectiveMonth);
            return;
        }

        LocalDate deadline = contractEffectiveFrom.withDayOfMonth(1).minusDays(15);
        if (deadline.isBefore(LocalDate.now())) {
            deadline = LocalDate.now();
        }

        InsuranceDeclaration entity = InsuranceDeclaration.builder()
                .companyId(companyId)
                .employeeId(employeeId)
                .declarationType(DeclarationType.INCREASE)
                .effectiveMonth(effectiveMonth)
                .deadline(deadline)
                .status(DeclarationStatus.PENDING)
                .reason(reason)
                .build();

        declarationRepository.save(entity);
    }

    /**
     * Tự động tạo báo GIẢM khi NV nghỉ việc.
     * Logic mốc ngày 15:
     *   - Nghỉ trước ngày 15 → deadline = ngày 15 tháng đó, effective = tháng đó
     *   - Nghỉ từ ngày 15 trở đi → deadline = ngày 1 tháng sau, effective = tháng sau
     */
    @Transactional
    public void createDecreaseDeclaration(Long companyId, Long employeeId,
                                           LocalDate lastWorkingDay, String reason) {
        LocalDate deadline;
        String effectiveMonth;

        if (lastWorkingDay.getDayOfMonth() < 15) {
            deadline = lastWorkingDay.withDayOfMonth(15);
            effectiveMonth = lastWorkingDay.format(MONTH_FMT);
        } else {
            LocalDate nextMonth = lastWorkingDay.plusMonths(1).withDayOfMonth(1);
            deadline = nextMonth;
            effectiveMonth = nextMonth.format(MONTH_FMT);
        }

        // Bug 8: Skip nếu đã có khai báo đang xử lý cho tháng này
        if (hasPendingDeclaration(companyId, employeeId, effectiveMonth)) {
            log.info("Skipped auto-create DECREASE for employee={}, month={}: already has pending declaration",
                    employeeId, effectiveMonth);
            return;
        }

        InsuranceDeclaration entity = InsuranceDeclaration.builder()
                .companyId(companyId)
                .employeeId(employeeId)
                .declarationType(DeclarationType.DECREASE)
                .effectiveMonth(effectiveMonth)
                .deadline(deadline)
                .status(DeclarationStatus.PENDING)
                .reason(reason)
                .build();

        declarationRepository.save(entity);
    }

    // ==================== VALIDATION HELPERS ====================

    /**
     * Chặn tạo duplicate: 1 NV chỉ có tối đa 1 khai báo PENDING/SUBMITTED cho 1 tháng.
     * Khai báo đã APPROVED/REJECTED không bị chặn (đã xong, có thể tạo mới).
     */
    private void assertNoDuplicateDeclaration(Long companyId, Long employeeId, String effectiveMonth) {
        long count = declarationRepository.countActiveByEmployeeAndMonth(companyId, employeeId, effectiveMonth);
        if (count > 0) {
            throw new BusinessException(
                    "Nhân sự đã có khai báo bảo hiểm đang chờ xử lý (PENDING/SUBMITTED) cho tháng " +
                    effectiveMonth + ". Vui lòng duyệt hoặc từ chối khai báo cũ trước khi tạo mới.",
                    HttpStatus.CONFLICT, "DUPLICATE_DECLARATION");
        }
    }

    private boolean hasPendingDeclaration(Long companyId, Long employeeId, String effectiveMonth) {
        return declarationRepository.countActiveByEmployeeAndMonth(companyId, employeeId, effectiveMonth) > 0;
    }

    // ==================== MAPPER ====================

    private InsuranceDeclarationResponse toResponse(InsuranceDeclaration e) {
        String employeeName = null;
        String employeeCode = null;
        var empOpt = employeeRepository.findById(e.getEmployeeId());
        if (empOpt.isPresent()) {
            Employee emp = empOpt.get();
            employeeName = emp.getFullName();
            employeeCode = emp.getEmployeeCode();
        }
        return new InsuranceDeclarationResponse(
                e.getId(), e.getEmployeeId(), employeeName, employeeCode,
                e.getDeclarationType(), e.getEffectiveMonth(), e.getDeadline(),
                e.getStatus(), e.getOldSalary(), e.getNewSalary(),
                e.getReason(), e.getCreatedAt());
    }
}
