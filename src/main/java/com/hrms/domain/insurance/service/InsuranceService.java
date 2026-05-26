package com.hrms.domain.insurance.service;

import com.hrms.domain.common.enums.InsurancePolicyStatus;
import com.hrms.domain.common.enums.InsuranceType;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.insurance.dto.*;
import com.hrms.domain.insurance.entity.InsuranceConfig;
import com.hrms.domain.insurance.entity.InsuranceHistory;
import com.hrms.domain.insurance.entity.InsurancePolicy;
import com.hrms.domain.insurance.repository.InsuranceConfigRepository;
import com.hrms.domain.insurance.repository.InsuranceHistoryRepository;
import com.hrms.domain.insurance.repository.InsurancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final InsuranceConfigRepository configRepository;
    private final InsurancePolicyRepository policyRepository;
    private final InsuranceHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;

    // ==================== CONFIG ====================

    @Transactional(readOnly = true)
    public List<InsuranceConfigResponse> getConfigs(Long companyId) {
        return configRepository.findByCompanyIdOrderByInsuranceTypeAscEffectiveFromDesc(companyId)
                .stream().map(this::toConfigResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsuranceConfigResponse> getActiveConfigs(Long companyId) {
        return configRepository.findAllActiveConfigs(companyId, LocalDate.now())
                .stream().map(this::toConfigResponse).toList();
    }

    @Transactional
    public InsuranceConfigResponse createConfig(Long companyId, InsuranceConfigRequest request) {
        InsuranceConfig entity = InsuranceConfig.builder()
                .companyId(companyId)
                .insuranceType(request.insuranceType())
                .employeeRate(request.employeeRate())
                .employerRate(request.employerRate())
                .minSalary(request.minSalary())
                .maxSalary(request.maxSalary())
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(request.effectiveTo())
                .build();
        return toConfigResponse(configRepository.save(entity));
    }

    @Transactional
    public InsuranceConfigResponse updateConfig(Long id, Long companyId, InsuranceConfigRequest request) {
        InsuranceConfig entity = configRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("InsuranceConfig", id));

        entity.setEmployeeRate(request.employeeRate());
        entity.setEmployerRate(request.employerRate());
        entity.setMinSalary(request.minSalary());
        entity.setMaxSalary(request.maxSalary());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());

        return toConfigResponse(configRepository.save(entity));
    }

    // ==================== POLICY ====================

    @Transactional(readOnly = true)
    public List<InsurancePolicyResponse> getPolicies(Long companyId) {
        return policyRepository.findByCompanyIdAndDeletedAtIsNullOrderByEmployeeIdAsc(companyId)
                .stream().map(this::toPolicyResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicyResponse> getActivePolicies(Long companyId) {
        return policyRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, InsurancePolicyStatus.ACTIVE)
                .stream().map(this::toPolicyResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicyResponse> getByEmployee(Long employeeId, Long companyId) {
        return policyRepository.findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByStartDateDesc(employeeId, companyId)
                .stream().map(this::toPolicyResponse).toList();
    }

    @Transactional
    public InsurancePolicyResponse createPolicy(Long companyId, InsurancePolicyRequest request) {
        // Kiểm tra NV đã có policy ACTIVE chưa
        if (policyRepository.existsByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                request.employeeId(), companyId, InsurancePolicyStatus.ACTIVE)) {
            throw new BusinessException("Employee already has an active insurance policy");
        }

        InsurancePolicy entity = InsurancePolicy.builder()
                .companyId(companyId)
                .employeeId(request.employeeId())
                .insuranceSalary(request.insuranceSalary())
                .socialInsuranceNumber(request.socialInsuranceNumber())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(InsurancePolicyStatus.ACTIVE)
                .notes(request.notes())
                .build();

        return toPolicyResponse(policyRepository.save(entity));
    }

    @Transactional
    public InsurancePolicyResponse updatePolicy(Long id, Long companyId, InsurancePolicyRequest request) {
        InsurancePolicy entity = policyRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("InsurancePolicy", id));

        // Log thay đổi insurance_salary
        if (entity.getInsuranceSalary().compareTo(request.insuranceSalary()) != 0) {
            historyRepository.save(InsuranceHistory.builder()
                    .insurancePolicyId(entity.getId())
                    .changedField("insurance_salary")
                    .oldValue(entity.getInsuranceSalary().toPlainString())
                    .newValue(request.insuranceSalary().toPlainString())
                    .build());
        }

        entity.setInsuranceSalary(request.insuranceSalary());
        entity.setSocialInsuranceNumber(request.socialInsuranceNumber());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setNotes(request.notes());

        return toPolicyResponse(policyRepository.save(entity));
    }

    @Transactional
    public void closePolicy(Long id, Long companyId, LocalDate endDate) {
        InsurancePolicy entity = policyRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("InsurancePolicy", id));

        historyRepository.save(InsuranceHistory.builder()
                .insurancePolicyId(entity.getId())
                .changedField("status")
                .oldValue(entity.getStatus().name())
                .newValue(InsurancePolicyStatus.CLOSED.name())
                .build());

        entity.setStatus(InsurancePolicyStatus.CLOSED);
        entity.setEndDate(endDate);
        policyRepository.save(entity);
    }

    // ==================== TÍNH TIỀN BH ====================

    /**
     * Tính tiền BH hàng tháng cho một nhân viên.
     * Lấy insurance_salary từ policy ACTIVE × tỷ lệ từ config.
     *
     * @param referenceDate ngày tham chiếu để lấy config (đầu tháng bảng lương, KHÔNG PHẢI today)
     */
    @Transactional(readOnly = true)
    public InsuranceCalculationResult calculateMonthly(Long employeeId, Long companyId, LocalDate referenceDate) {
        var policyOpt = policyRepository.findByEmployeeIdAndCompanyIdAndStatusAndDeletedAtIsNull(
                employeeId, companyId, InsurancePolicyStatus.ACTIVE);

        if (policyOpt.isEmpty()) {
            return InsuranceCalculationResult.zero();
        }

        InsurancePolicy policy = policyOpt.get();
        BigDecimal baseSalary = policy.getInsuranceSalary();

        // Lấy config tại tháng bảng lương. Nếu không có (config được tạo sau tháng này),
        // fallback lấy config mới nhất đang mở (effectiveTo IS NULL) — tỷ lệ BH thường
        // được áp dụng hồi tố khi cấu hình muộn hơn tháng tính lương.
        List<InsuranceConfig> configs = configRepository.findAllActiveConfigs(companyId, referenceDate);
        if (configs.isEmpty()) {
            configs = configRepository.findLatestOpenConfigs(companyId);
            // Deduplicate: mỗi InsuranceType chỉ giữ 1 config mới nhất
            configs = configs.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            InsuranceConfig::getInsuranceType,
                            c -> c,
                            (existing, replacement) -> existing)) // effectiveFrom DESC → first wins
                    .values().stream().toList();
        }

        if (configs.isEmpty()) {
            return InsuranceCalculationResult.zero();
        }

        BigDecimal employeeTotal = BigDecimal.ZERO;
        BigDecimal employerTotal = BigDecimal.ZERO;
        BigDecimal bhxhEmp = BigDecimal.ZERO, bhxhEr = BigDecimal.ZERO;
        BigDecimal bhytEmp = BigDecimal.ZERO, bhytEr = BigDecimal.ZERO;
        BigDecimal bhtnEmp = BigDecimal.ZERO, bhtnEr = BigDecimal.ZERO;
        BigDecimal hundred = new BigDecimal("100");

        for (InsuranceConfig config : configs) {
            // Bug 2 (từ review trước): Clamp min/max PER-TYPE, không dùng chung
            BigDecimal clampedSalary = baseSalary;
            if (config.getMinSalary() != null && clampedSalary.compareTo(config.getMinSalary()) < 0) {
                clampedSalary = config.getMinSalary();
            }
            if (config.getMaxSalary() != null && clampedSalary.compareTo(config.getMaxSalary()) > 0) {
                clampedSalary = config.getMaxSalary();
            }

            BigDecimal empAmount = clampedSalary.multiply(config.getEmployeeRate())
                    .divide(hundred, 0, RoundingMode.HALF_UP);
            BigDecimal erAmount = clampedSalary.multiply(config.getEmployerRate())
                    .divide(hundred, 0, RoundingMode.HALF_UP);

            switch (config.getInsuranceType()) {
                case BHXH -> { bhxhEmp = empAmount; bhxhEr = erAmount; }
                case BHYT -> { bhytEmp = empAmount; bhytEr = erAmount; }
                case BHTN -> { bhtnEmp = empAmount; bhtnEr = erAmount; }
            }
            employeeTotal = employeeTotal.add(empAmount);
            employerTotal = employerTotal.add(erAmount);
        }

        return new InsuranceCalculationResult(baseSalary, employeeTotal, employerTotal,
                bhxhEmp, bhxhEr, bhytEmp, bhytEr, bhtnEmp, bhtnEr);
    }

    // ==================== HISTORY ====================

    @Transactional(readOnly = true)
    public List<InsuranceHistory> getHistory(Long policyId) {
        return historyRepository.findByInsurancePolicyIdOrderByChangedAtDesc(policyId);
    }

    // ==================== SEED DEFAULT CONFIG ====================

    /**
     * Tạo config mặc định khi tạo company mới.
     * Tỷ lệ theo quy định 2024-2025.
     */
    @Transactional
    public void seedDefaultConfig(Long companyId) {
        LocalDate now = LocalDate.now();

        configRepository.save(InsuranceConfig.builder()
                .companyId(companyId).insuranceType(InsuranceType.BHXH)
                .employeeRate(new BigDecimal("8.00")).employerRate(new BigDecimal("17.50"))
                .minSalary(new BigDecimal("2340000")).maxSalary(new BigDecimal("46800000"))
                .effectiveFrom(now).build());

        configRepository.save(InsuranceConfig.builder()
                .companyId(companyId).insuranceType(InsuranceType.BHYT)
                .employeeRate(new BigDecimal("1.50")).employerRate(new BigDecimal("3.00"))
                .minSalary(new BigDecimal("2340000")).maxSalary(new BigDecimal("46800000"))
                .effectiveFrom(now).build());

        configRepository.save(InsuranceConfig.builder()
                .companyId(companyId).insuranceType(InsuranceType.BHTN)
                .employeeRate(new BigDecimal("1.00")).employerRate(new BigDecimal("1.00"))
                .minSalary(new BigDecimal("2340000")).maxSalary(new BigDecimal("46800000"))
                .effectiveFrom(now).build());
    }

    // ==================== MAPPERS ====================

    private InsuranceConfigResponse toConfigResponse(InsuranceConfig e) {
        return new InsuranceConfigResponse(
                e.getId(), e.getInsuranceType(), e.getEmployeeRate(), e.getEmployerRate(),
                e.getMinSalary(), e.getMaxSalary(), e.getEffectiveFrom(), e.getEffectiveTo(),
                e.getCreatedAt());
    }

    private InsurancePolicyResponse toPolicyResponse(InsurancePolicy e) {
        String employeeName = null;
        String employeeCode = null;
        var empOpt = employeeRepository.findById(e.getEmployeeId());
        if (empOpt.isPresent()) {
            Employee emp = empOpt.get();
            employeeName = emp.getFullName();
            employeeCode = emp.getEmployeeCode();
        }
        return new InsurancePolicyResponse(
                e.getId(), e.getEmployeeId(), employeeName, employeeCode,
                e.getInsuranceSalary(), e.getSocialInsuranceNumber(),
                e.getStartDate(), e.getEndDate(), e.getStatus(),
                e.getNotes(), e.getCreatedAt());
    }
}
