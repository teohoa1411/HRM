package com.hrms.domain.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.company.entity.Company;
import com.hrms.domain.company.entity.Department;
import com.hrms.domain.company.entity.Position;
import com.hrms.domain.company.repository.CompanyRepository;
import com.hrms.domain.company.repository.DepartmentRepository;
import com.hrms.domain.company.repository.PositionRepository;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.calendar.service.WorkCalendarService;
import com.hrms.domain.finance.service.FinancialReportService;
import com.hrms.domain.internal_statement.entity.InternalStatementEntry;
import com.hrms.domain.internal_statement.entity.InternalStatementSummary;
import com.hrms.domain.internal_statement.repository.InternalStatementEntryRepository;
import com.hrms.domain.internal_statement.repository.InternalStatementSummaryRepository;
import com.hrms.domain.insurance.dto.InsuranceCalculationResult;
import com.hrms.domain.insurance.service.InsuranceService;
import com.hrms.domain.jobrole.entity.JobRole;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.domain.payroll.dto.PayrollAdjustmentDto;
import com.hrms.domain.payroll.dto.PayrollRecordDto;
import com.hrms.domain.payroll.dto.PayrollReadinessDto;
import com.hrms.domain.payroll.dto.PayrollReadinessEmployeeDto;
import com.hrms.domain.payroll.dto.UpdatePayrollRequest;
import com.hrms.domain.payroll.entity.PayrollAdjustment;
import com.hrms.domain.payroll.entity.PayrollRecord;
import com.hrms.domain.payroll.entity.PayrollRecordHistory;
import com.hrms.domain.payroll.repository.PayrollAdjustmentRepository;
import com.hrms.domain.payroll.repository.PayrollRecordHistoryRepository;
import com.hrms.domain.timesheet.repository.TimesheetPeriodLockRepository;
import com.hrms.domain.payroll.repository.PayrollRecordRepository;
import com.hrms.domain.payroll.repository.PayrollCostAllocationRepository;
import com.hrms.domain.payroll.entity.PayrollCostAllocation;
import com.hrms.domain.salary.repository.InternalSalaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRecordRepository payrollRecordRepository;
    private final PayrollRecordHistoryRepository historyRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final JobRoleRepository jobRoleRepository;
    private final TimesheetPeriodLockRepository timesheetPeriodLockRepository;
    private final FinancialReportService financialReportService;
    private final InsuranceService insuranceService;
    private final InternalStatementSummaryRepository internalStatementSummaryRepository;
    private final InternalStatementEntryRepository internalStatementEntryRepository;
    private final InternalSalaryRepository internalSalaryRepository;
    private final WorkCalendarService workCalendarService;
    private final PayrollCostAllocationRepository payrollCostAllocationRepository;
    private final ObjectMapper objectMapper;

    /** Sinh PayrollRecord từ BillingRecord (INTERNAL) cho tháng chỉ định.
     *  Sau V29 mỗi NV có thể có nhiều billing record (period). Payroll vẫn 1 NV / 1 tháng
     *  → aggregate sum daysWorked + costAmount của tất cả period. */
    @Transactional
    public List<PayrollRecordDto> generatePayroll(Long companyId, String yearMonth) {
        // Bug 4: Chặn tạo bảng lương tháng tương lai
        YearMonth payrollYm = YearMonth.parse(yearMonth);
        YearMonth currentYm = YearMonth.now();
        if (payrollYm.isAfter(currentYm)) {
            throw new BusinessException(
                    "Không thể tạo bảng lương cho tháng tương lai: " + yearMonth,
                    HttpStatus.BAD_REQUEST, "FUTURE_PAYROLL");
        }

        // Luôn tính lại billing trước khi sinh bảng lương để đảm bảo dữ liệu
        // lương/giá mới nhất được phản ánh (tránh stale billing sau khi thay đổi lương).
        // Bug 1: Dùng ngày đầu tháng bảng lương làm referenceDate cho insurance
        LocalDate insuranceRefDate = payrollYm.atDay(1);

        List<Employee> eligibleEmployees = employeeRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .filter(e -> e.getSourceType() == EmployeeSourceType.INTERNAL)
                .filter(e -> !e.getStartDate().isAfter(payrollYm.atEndOfMonth()))
                .filter(e -> e.getEndDate() == null || !e.getEndDate().isBefore(payrollYm.atDay(1)))
                .toList();
        Map<Long, InternalStatementSummary> confirmed = internalStatementSummaryRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth).stream()
                .filter(s -> "CONFIRMED".equals(s.getStatus()))
                .collect(Collectors.toMap(InternalStatementSummary::getEmployeeId, Function.identity()));
        List<String> missing = eligibleEmployees.stream().filter(e -> !confirmed.containsKey(e.getId()))
                .map(Employee::getEmployeeCode).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("Chưa xác nhận Biên bản nội bộ cho: " + String.join(", ", missing),
                    HttpStatus.CONFLICT, "INTERNAL_STATEMENT_NOT_CONFIRMED");
        }
        BigDecimal totalWorkdays = BigDecimal.valueOf(workCalendarService.getTotalWorkdays(companyId, yearMonth));
        if (totalWorkdays.signum() <= 0) totalWorkdays = BigDecimal.valueOf(22);

        List<PayrollRecord> results = new ArrayList<>();
        for (Employee employee : eligibleEmployees) {
            Long empId = employee.getId();
            InternalStatementSummary statement = confirmed.get(empId);
            BigDecimal monthlySalary = internalSalaryRepository.findSalaryAt(empId, insuranceRefDate)
                    .map(s -> s.getMonthlySalary()).orElse(BigDecimal.ZERO);
            BigDecimal totalDaysWorked = statement.getTotalAdjustedDays();
            BigDecimal totalDaysOff = totalWorkdays.subtract(totalDaysWorked).max(BigDecimal.ZERO);
            BigDecimal hourlyRate = monthlySalary.divide(totalWorkdays.multiply(BigDecimal.valueOf(8)), 8,
                    java.math.RoundingMode.HALF_UP);
            BigDecimal totalCost = hourlyRate.multiply(statement.getTotalAdjustedHours())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal overtimeAmount = internalStatementEntryRepository
                    .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, empId, yearMonth).stream()
                    .map(e -> hourlyRate.multiply(e.getAdjustedOtHours()).multiply(e.getOtMultiplier())
                            .setScale(2, java.math.RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PayrollRecord record = payrollRecordRepository
                    .findByCompanyIdAndEmployeeIdAndPayrollMonth(companyId, empId, yearMonth)
                    .orElse(null);

            // Bug 1: Truyền tháng bảng lương thay vì dùng LocalDate.now()
            InsuranceCalculationResult insResult = insuranceService.calculateMonthly(empId, companyId, insuranceRefDate);

            if (record == null) {
                record = PayrollRecord.builder()
                        .companyId(companyId)
                        .employeeId(empId)
                        .payrollMonth(yearMonth)
                        .baseSalary(monthlySalary)
                        .daysWorked(totalDaysWorked)
                        .daysOff(totalDaysOff)
                        .totalWorkdays(totalWorkdays)
                        .calculatedSalary(totalCost)
                        .overtimeAmount(overtimeAmount)
                        .sourceStatementRevision(statement.getRevision())
                        .bonus(BigDecimal.ZERO)
                        .deduction(BigDecimal.ZERO)
                        .insuranceSalary(insResult.insuranceSalary())
                        .insuranceEmployeeAmount(insResult.employeeAmount())
                        .insuranceEmployerAmount(insResult.employerAmount())
                        .currency("VND")
                        .status("DRAFT")
                        .build();
                record.setBhxhEmployeeAmount(insResult.bhxhEmployee());
                record.setBhxhEmployerAmount(insResult.bhxhEmployer());
                record.setBhytEmployeeAmount(insResult.bhytEmployee());
                record.setBhytEmployerAmount(insResult.bhytEmployer());
                record.setBhtnEmployeeAmount(insResult.bhtnEmployee());
                record.setBhtnEmployerAmount(insResult.bhtnEmployer());
                // net = calculatedSalary - insuranceEmployeeAmount (chưa có bonus/deduction)
                record.recalculateNetSalary();
            } else if ("DRAFT".equals(record.getStatus()) || "ADJUSTED".equals(record.getStatus()) || "STALE".equals(record.getStatus())) {
                record.setBaseSalary(monthlySalary);
                record.setDaysWorked(totalDaysWorked);
                record.setDaysOff(totalDaysOff);
                record.setTotalWorkdays(totalWorkdays);
                record.setCalculatedSalary(totalCost);
                record.setOvertimeAmount(overtimeAmount);
                record.setSourceStatementRevision(statement.getRevision());
                record.setStatus("DRAFT");
                // Cập nhật BH mỗi lần regenerate
                record.setInsuranceSalary(insResult.insuranceSalary());
                record.setInsuranceEmployeeAmount(insResult.employeeAmount());
                record.setInsuranceEmployerAmount(insResult.employerAmount());
                record.setBhxhEmployeeAmount(insResult.bhxhEmployee());
                record.setBhxhEmployerAmount(insResult.bhxhEmployer());
                record.setBhytEmployeeAmount(insResult.bhytEmployee());
                record.setBhytEmployerAmount(insResult.bhytEmployer());
                record.setBhtnEmployeeAmount(insResult.bhtnEmployee());
                record.setBhtnEmployerAmount(insResult.bhtnEmployer());
                record.recalculateNetSalary();
            }

            record = payrollRecordRepository.save(record);

            // Bug 4: Warning khi net_salary âm
            if (record.getNetSalary() != null && record.getNetSalary().compareTo(BigDecimal.ZERO) < 0) {
                log.warn("NEGATIVE NET SALARY: employee={}, month={}, net={}, calculated={}, bonus={}, deduction={}, insurance={}",
                        empId, yearMonth, record.getNetSalary(), record.getCalculatedSalary(),
                        record.getBonus(), record.getDeduction(), record.getInsuranceEmployeeAmount());
            }

            results.add(record);
        }

        log.info("Generated {} payroll records for company {} month {}", results.size(), companyId, yearMonth);
        return toDto(results, companyId);
    }

    /** Lấy danh sách PayrollRecord cho tháng */
    @Transactional(readOnly = true)
    public List<PayrollRecordDto> getPayroll(Long companyId, String yearMonth) {
        List<PayrollRecord> records = payrollRecordRepository
                .findByCompanyIdAndPayrollMonth(companyId, yearMonth);
        return toDto(records, companyId);
    }

    @Transactional(readOnly = true)
    public PayrollReadinessDto getReadiness(Long companyId, String yearMonth) {
        YearMonth month = YearMonth.parse(yearMonth);
        LocalDate refDate = month.atDay(1);
        List<Employee> eligible = employeeRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .filter(e -> e.getSourceType() == EmployeeSourceType.INTERNAL)
                .filter(e -> !e.getStartDate().isAfter(month.atEndOfMonth()))
                .filter(e -> e.getEndDate() == null || !e.getEndDate().isBefore(refDate)).toList();
        Map<Long, InternalStatementSummary> summaries = internalStatementSummaryRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth).stream()
                .collect(Collectors.toMap(InternalStatementSummary::getEmployeeId, Function.identity()));
        Map<Long, PayrollRecord> payrolls = payrollRecordRepository.findByCompanyIdAndPayrollMonth(companyId, yearMonth)
                .stream().collect(Collectors.toMap(PayrollRecord::getEmployeeId, Function.identity()));
        List<PayrollReadinessEmployeeDto> missing = new ArrayList<>();
        List<PayrollReadinessEmployeeDto> unconfirmed = new ArrayList<>();
        List<PayrollReadinessEmployeeDto> missingSalary = new ArrayList<>();
        List<PayrollReadinessEmployeeDto> stale = new ArrayList<>();
        int confirmed = 0;
        for (Employee employee : eligible) {
            InternalStatementSummary summary = summaries.get(employee.getId());
            if (summary == null) missing.add(readinessEmployee(employee, "Chưa có Biên bản nội bộ"));
            else if (!"CONFIRMED".equals(summary.getStatus())) unconfirmed.add(readinessEmployee(employee, "Biên bản chưa xác nhận"));
            else confirmed++;
            if (internalSalaryRepository.findSalaryAt(employee.getId(), refDate).isEmpty()) {
                missingSalary.add(readinessEmployee(employee, "Chưa có lương hiệu lực"));
            }
            if (payrolls.get(employee.getId()) != null && "STALE".equals(payrolls.get(employee.getId()).getStatus())) {
                stale.add(readinessEmployee(employee, "Payroll cần tạo lại"));
            }
        }
        List<String> blockers = new ArrayList<>();
        if (!missing.isEmpty()) blockers.add("Có nhân sự chưa có Biên bản nội bộ");
        if (!unconfirmed.isEmpty()) blockers.add("Có Biên bản nội bộ chưa xác nhận");
        if (!missingSalary.isEmpty()) blockers.add("Có nhân sự chưa có lương hiệu lực");
        if (!stale.isEmpty()) blockers.add("Có Payroll stale cần tạo lại");
        return new PayrollReadinessDto(blockers.isEmpty(), eligible.size(), confirmed, missing, unconfirmed, missingSalary, stale, blockers);
    }

    private PayrollReadinessEmployeeDto readinessEmployee(Employee employee, String reason) {
        return new PayrollReadinessEmployeeDto(employee.getId(), employee.getPublicId(), employee.getEmployeeCode(), employee.getFullName(), reason);
    }

    /** Cập nhật bonus/deduction/note hoặc adjustments chi tiết */
    @Transactional
    public PayrollRecordDto updatePayrollRecord(Long companyId, Long id, UpdatePayrollRequest req) {
        PayrollRecord record = payrollRecordRepository.findById(id)
                .filter(r -> r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Payroll record not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!List.of("DRAFT", "ADJUSTED").contains(record.getStatus()) || "PAID".equals(record.getStatus())) {
            throw new BusinessException("Cannot edit confirmed payroll record", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        // Khi client gửi adjustments, thay thế toàn bộ danh sách (kể cả danh sách rỗng).
        // Danh sách rỗng nghĩa là người dùng đã xóa tất cả các khoản phát sinh.
        if (req.adjustments() != null) {
            List<PayrollAdjustment> newAdjs = new ArrayList<>();
            int order = 0;
            for (PayrollAdjustmentDto dto : req.adjustments()) {
                newAdjs.add(PayrollAdjustment.builder()
                        .payrollRecordId(record.getId())
                        .type(dto.type())
                        .description(dto.description())
                        .amount(dto.amount())
                        .taxable(dto.taxable() != null ? dto.taxable() : true)
                        .sortOrder(dto.sortOrder() != null ? dto.sortOrder() : order++)
                        .build());
            }
            record.getAdjustments().clear();
            record.getAdjustments().addAll(newAdjs);
            // Không để các tổng legacy còn sót lại khi danh sách mới rỗng.
            record.setBonus(BigDecimal.ZERO);
            record.setDeduction(BigDecimal.ZERO);
        } else {
            // Fallback: dùng bonus/deduction đơn lẻ (backward compatible)
            if (req.bonus() != null) record.setBonus(req.bonus());
            if (req.deduction() != null) record.setDeduction(req.deduction());
        }

        if (req.adjustmentNote() != null) record.setAdjustmentNote(req.adjustmentNote());
        record.setStatus("ADJUSTED");
        record.recalculateNetSalary();

        PayrollRecord saved = payrollRecordRepository.save(record);
        Employee emp = employeeRepository.findById(saved.getEmployeeId()).orElse(null);
        return toSingleDto(saved, emp);
    }

    /** Thêm một khoản phát sinh vào payroll record */
    @Transactional
    public PayrollAdjustmentDto addAdjustment(Long companyId, Long payrollRecordId, PayrollAdjustmentDto dto) {
        PayrollRecord record = payrollRecordRepository.findById(payrollRecordId)
                .filter(r -> r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Payroll record not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!List.of("DRAFT", "ADJUSTED").contains(record.getStatus())) {
            throw new BusinessException("Cannot edit confirmed payroll record", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        PayrollAdjustment adj = PayrollAdjustment.builder()
                .payrollRecordId(payrollRecordId)
                .type(dto.type())
                .description(dto.description())
                .amount(dto.amount())
                .taxable(dto.taxable() != null ? dto.taxable() : true)
                .sortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0)
                .build();
        adj = adjustmentRepository.save(adj);

        // Recalculate
        record.setAdjustments(adjustmentRepository.findByPayrollRecordIdOrderBySortOrderAsc(payrollRecordId));
        record.setStatus("ADJUSTED");
        record.recalculateNetSalary();
        payrollRecordRepository.save(record);

        return toAdjustmentDto(adj);
    }

    /** Xóa một khoản phát sinh */
    @Transactional
    public void removeAdjustment(Long companyId, Long payrollRecordId, Long adjustmentId) {
        PayrollRecord record = payrollRecordRepository.findById(payrollRecordId)
                .filter(r -> r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Payroll record not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!List.of("DRAFT", "ADJUSTED").contains(record.getStatus())) {
            throw new BusinessException("Cannot edit confirmed payroll record", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        adjustmentRepository.deleteById(adjustmentId);

        // Recalculate
        record.setAdjustments(adjustmentRepository.findByPayrollRecordIdOrderBySortOrderAsc(payrollRecordId));
        record.setStatus("ADJUSTED");
        record.recalculateNetSalary();
        payrollRecordRepository.save(record);
    }

    /** Lấy danh sách khoản phát sinh cho một payroll record */
    @Transactional(readOnly = true)
    public List<PayrollAdjustmentDto> getAdjustments(Long payrollRecordId) {
        return adjustmentRepository.findByPayrollRecordIdOrderBySortOrderAsc(payrollRecordId)
                .stream().map(this::toAdjustmentDto).toList();
    }

    /** Xác nhận toàn bộ PayrollRecord trong tháng */
    @Transactional
    public void confirmPayroll(Long companyId, String yearMonth, Long confirmedBy) {
        List<PayrollRecord> records = payrollRecordRepository
                .findByCompanyIdAndPayrollMonth(companyId, yearMonth);

        if (records.isEmpty()) {
            throw new BusinessException("No payroll records found for " + yearMonth, HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        for (PayrollRecord record : records) {
            InternalStatementSummary source = internalStatementSummaryRepository
                    .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, record.getEmployeeId(), yearMonth)
                    .orElseThrow(() -> new BusinessException("Thiếu Biên bản nội bộ nguồn", HttpStatus.CONFLICT, "PAYROLL_STALE"));
            if (!"CONFIRMED".equals(source.getStatus()) || source.getRevision() != record.getSourceStatementRevision()) {
                record.setStatus("STALE");
                payrollRecordRepository.save(record);
                throw new BusinessException("Dữ liệu công đã thay đổi, hãy tạo lại Payroll", HttpStatus.CONFLICT, "PAYROLL_STALE");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (PayrollRecord r : records) {
            if (!"CONFIRMED".equals(r.getStatus()) && !"EXPORTED".equals(r.getStatus()) && !"PAID".equals(r.getStatus())) {
                r.setStatus("CONFIRMED");
                r.setConfirmedBy(confirmedBy);
                r.setConfirmedAt(now);
            }
        }
        payrollRecordRepository.saveAll(records);
        log.info("Confirmed payroll for company {} month {} by user {}", companyId, yearMonth, confirmedBy);

        // Mandatory derived data: an exception rolls back the payroll confirmation.
        syncCostAllocations(companyId, yearMonth, records);
        financialReportService.generateMonthlyFinancialRecords(companyId, yearMonth);
    }

    private void syncCostAllocations(Long companyId, String yearMonth, List<PayrollRecord> records) {
        payrollCostAllocationRepository.deleteByCompanyIdAndPayrollMonth(companyId, yearMonth);
        for (PayrollRecord payroll : records) {
            Map<Long, BigDecimal> sellByCustomer = billingRecordRepository
                    .findByCompanyAndEmployeeAndMonth(companyId, payroll.getEmployeeId(), yearMonth).stream()
                    .filter(b -> b.getCustomerId() != null)
                    .collect(Collectors.groupingBy(BillingRecord::getCustomerId,
                            Collectors.reducing(BigDecimal.ZERO, BillingRecord::getSellAmount, BigDecimal::add)));
            BigDecimal totalSell = sellByCustomer.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = payroll.getNetSalary();
            if (sellByCustomer.isEmpty() || totalSell.signum() <= 0) {
                payrollCostAllocationRepository.save(PayrollCostAllocation.builder()
                        .companyId(companyId).payrollRecordId(payroll.getId()).employeeId(payroll.getEmployeeId())
                        .payrollMonth(yearMonth).customerId(null).allocationRatio(BigDecimal.ONE)
                        .allocatedAmount(target).build());
                continue;
            }
            BigDecimal assigned = BigDecimal.ZERO;
            List<Map.Entry<Long, BigDecimal>> customerSell = new ArrayList<>(sellByCustomer.entrySet());
            for (int i = 0; i < customerSell.size(); i++) {
                Map.Entry<Long, BigDecimal> customer = customerSell.get(i);
                BigDecimal amount = i == customerSell.size() - 1 ? target.subtract(assigned)
                        : target.multiply(customer.getValue()).divide(totalSell, 2, java.math.RoundingMode.HALF_UP);
                payrollCostAllocationRepository.save(PayrollCostAllocation.builder()
                        .companyId(companyId).payrollRecordId(payroll.getId()).employeeId(payroll.getEmployeeId())
                        .payrollMonth(yearMonth).customerId(customer.getKey())
                        .allocationRatio(customer.getValue().divide(totalSell, 6, java.math.RoundingMode.HALF_UP))
                        .allocatedAmount(amount).build());
                assigned = assigned.add(amount);
            }
        }
    }

    /** Reset về DRAFT và lưu lịch sử */
    @Transactional
    public void resetPayroll(Long companyId, String yearMonth, String reason, Long changedBy) {
        // Chặn reset khi đã PAID
        List<PayrollRecord> paidCheck = payrollRecordRepository.findByCompanyIdAndPayrollMonth(companyId, yearMonth);
        if (paidCheck.stream().anyMatch(r -> "PAID".equals(r.getStatus()))) {
            throw new BusinessException("Bảng lương tháng " + yearMonth + " đã trả lương. Không thể reset.",
                    HttpStatus.FORBIDDEN, "PAYROLL_PAID");
        }

        // Chặn reset khi biên bản đã đóng
        timesheetPeriodLockRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .ifPresent(lock -> {
                    if (lock.isStatementClosed()) {
                        throw new BusinessException(
                                "Bảng lương tháng " + yearMonth + " đã bị khóa do biên bản đã đóng.",
                                HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
                    }
                });

        List<PayrollRecord> records = payrollRecordRepository
                .findByCompanyIdAndPayrollMonth(companyId, yearMonth);

        for (PayrollRecord r : records) {
            try {
                String snapshot = objectMapper.writeValueAsString(r);
                historyRepository.save(PayrollRecordHistory.builder()
                        .payrollRecordId(r.getId())
                        .snapshotData(snapshot)
                        .changeType("RESET_BY_ADMIN")
                        .reason(reason)
                        .changedBy(changedBy)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to serialize snapshot for payroll {}", r.getId());
            }
            r.setStatus("DRAFT");
            r.setConfirmedBy(null);
            r.setConfirmedAt(null);
            r.setExportedAt(null);
        }
        payrollRecordRepository.saveAll(records);
    }

    /** Đánh dấu bảng lương đã trả (PAID) */
    @Transactional
    public void markPaid(Long companyId, String yearMonth, Long paidBy) {
        List<PayrollRecord> records = payrollRecordRepository.findByCompanyIdAndPayrollMonth(companyId, yearMonth);
        if (records.isEmpty()) {
            throw new BusinessException("No payroll records found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        // Must be CONFIRMED or EXPORTED to mark as PAID
        for (PayrollRecord r : records) {
            if (!List.of("CONFIRMED", "EXPORTED").contains(r.getStatus())) {
                throw new BusinessException(
                        "Tất cả bảng lương phải ở trạng thái CONFIRMED hoặc EXPORTED trước khi đánh dấu đã trả.",
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (PayrollRecord r : records) {
            r.setStatus("PAID");
            r.setPaidBy(paidBy);
            r.setPaidAt(now);
        }
        payrollRecordRepository.saveAll(records);
        log.info("Marked payroll as PAID for company {} month {} by user {}", companyId, yearMonth, paidBy);
    }

    /** Xuất Excel bảng lương */
    public byte[] exportPayrollExcel(Long companyId, String yearMonth) {
        List<PayrollRecord> records = payrollRecordRepository
                .findByCompanyIdAndPayrollMonth(companyId, yearMonth);

        Map<Long, Employee> empMap = employeeRepository.findAllById(
                        records.stream().map(PayrollRecord::getEmployeeId).toList())
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, Department> departments = departmentRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().collect(Collectors.toMap(Department::getId, Function.identity()));
        Map<Long, Position> positions = positionRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().collect(Collectors.toMap(Position::getId, Function.identity()));
        Map<Long, JobRole> jobRoles = jobRoleRepository.findAll().stream()
                .filter(role -> companyId.equals(role.getCompanyId()) && role.getDeletedAt() == null)
                .collect(Collectors.toMap(JobRole::getId, Function.identity()));
        Map<Long, List<PayrollAdjustment>> adjustmentsByRecord = adjustmentRepository
                .findByPayrollRecordIdInOrderByPayrollRecordIdAscSortOrderAsc(
                        records.stream().map(PayrollRecord::getId).toList())
                .stream().collect(Collectors.groupingBy(PayrollAdjustment::getPayrollRecordId));
        Company company = companyRepository.findById(companyId).orElse(null);

        records.sort(Comparator.comparing(record -> {
            Employee employee = empMap.get(record.getEmployeeId());
            return employee != null ? employee.getEmployeeCode() : "";
        }));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(wb);
            Sheet sheet = wb.createSheet("Bảng lương tổng hợp");
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, records.size() + 2), 0, 23));
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG LƯƠNG NỘI BỘ - THÁNG " + yearMonth);
            titleCell.setCellStyle(styles.title());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 23));
            Row metaRow = sheet.createRow(1);
            Cell metaCell = metaRow.createCell(0);
            boolean preliminary = records.stream().anyMatch(record -> !List.of("CONFIRMED", "EXPORTED", "PAID").contains(record.getStatus()));
            metaCell.setCellValue((company != null ? company.getName() + " · " : "")
                    + "Xuất lúc " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    + (preliminary ? " · BẢN TẠM TÍNH" : " · BẢN ĐÃ CHỐT"));
            metaCell.setCellStyle(styles.meta());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 23));

            String[] headers = {"STT", "Mã NV", "Họ và tên", "Phòng ban", "Vị trí",
                    "Tháng", "Ngày làm", "Ngày nghỉ", "Lương CB", "Lương tính công", "Lương đóng BH",
                    "BHXH NV", "BHYT NV", "BHTN NV", "Tổng BH NV", "BHXH CTy", "BHYT CTy", "BHTN CTy",
                    "Tổng BH CTy", "Thưởng", "Khấu trừ", "Ghi chú KT", "Lương Net", "Trạng thái"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.header());
            }
            int[] widths = {8, 14, 26, 20, 20, 12, 11, 11, 16, 17, 16, 14, 14, 14, 15, 14, 14, 14, 15, 15, 15, 28, 17, 14};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

            int rowNum = 3;
            int stt = 1;
            for (PayrollRecord record : records) {
                Employee employee = empMap.get(record.getEmployeeId());
                Department department = employee != null ? departments.get(employee.getDepartmentId()) : null;
                Position position = employee != null ? positions.get(employee.getPositionId()) : null;
                JobRole jobRole = employee != null ? jobRoles.get(employee.getJobRoleId()) : null;
                Row row = sheet.createRow(rowNum++);
                writeText(row, 0, String.valueOf(stt++), styles.center());
                writeText(row, 1, employee != null ? employee.getEmployeeCode() : "", styles.text());
                writeText(row, 2, employee != null ? employee.getFullName() : "", styles.text());
                writeText(row, 3, department != null ? department.getName() : "", styles.text());
                writeText(row, 4, position != null ? position.getName() : (jobRole != null ? jobRole.getName() : ""), styles.text());
                writeText(row, 5, record.getPayrollMonth(), styles.center());
                writeNumber(row, 6, record.getDaysWorked(), styles.decimal());
                writeNumber(row, 7, record.getDaysOff(), styles.decimal());
                writeNumber(row, 8, record.getBaseSalary(), styles.money());
                writeNumber(row, 9, record.getCalculatedSalary(), styles.money());
                writeNumber(row, 10, record.getInsuranceSalary(), styles.money());
                writeNumber(row, 11, record.getBhxhEmployeeAmount(), styles.money());
                writeNumber(row, 12, record.getBhytEmployeeAmount(), styles.money());
                writeNumber(row, 13, record.getBhtnEmployeeAmount(), styles.money());
                writeNumber(row, 14, record.getInsuranceEmployeeAmount(), styles.money());
                writeNumber(row, 15, record.getBhxhEmployerAmount(), styles.money());
                writeNumber(row, 16, record.getBhytEmployerAmount(), styles.money());
                writeNumber(row, 17, record.getBhtnEmployerAmount(), styles.money());
                writeNumber(row, 18, record.getInsuranceEmployerAmount(), styles.money());
                writeNumber(row, 19, record.getBonus(), styles.positiveMoney());
                writeNumber(row, 20, record.getDeduction(), styles.negativeMoney());
                writeText(row, 21, record.getAdjustmentNote() != null ? record.getAdjustmentNote() : "", styles.text());
                writeNumber(row, 22, record.getNetSalary(), styles.netMoney());
                writeText(row, 23, record.getStatus(), styles.center());
            }

            Row totalRow = sheet.createRow(rowNum);
            writeText(totalRow, 0, "TỔNG CỘNG", styles.totalLabel());
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 7));
            int excelFirstDataRow = 4;
            int excelLastDataRow = Math.max(excelFirstDataRow, rowNum);
            for (int column : new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22}) {
                Cell cell = totalRow.createCell(column);
                cell.setCellFormula("SUM(" + columnLetter(column) + excelFirstDataRow + ":" + columnLetter(column) + excelLastDataRow + ")");
                cell.setCellStyle(column == 19 ? styles.positiveTotal() : column == 20 ? styles.negativeTotal() : column == 22 ? styles.netTotal() : styles.moneyTotal());
            }

            Sheet details = wb.createSheet("Chi tiết điều chỉnh");
            details.createFreezePane(0, 3);
            details.setAutoFilter(new CellRangeAddress(2, Math.max(2, adjustmentsByRecord.values().stream().mapToInt(List::size).sum() + 2), 0, 8));
            details.setFitToPage(true);
            Row detailTitle = details.createRow(0);
            Cell detailTitleCell = detailTitle.createCell(0);
            detailTitleCell.setCellValue("CHI TIẾT THƯỞNG & KHẤU TRỪ - THÁNG " + yearMonth);
            detailTitleCell.setCellStyle(styles.title());
            details.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
            Row detailMeta = details.createRow(1);
            Cell detailMetaCell = detailMeta.createCell(0);
            detailMetaCell.setCellValue("Dùng để đối soát các khoản phát sinh trước khi chi trả lương.");
            detailMetaCell.setCellStyle(styles.meta());
            details.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));
            String[] detailHeaders = {"STT", "Mã NV", "Họ và tên", "Loại", "Nội dung", "Số tiền", "Chịu thuế", "Tháng", "Trạng thái"};
            Row detailHeaderRow = details.createRow(2);
            for (int i = 0; i < detailHeaders.length; i++) {
                Cell cell = detailHeaderRow.createCell(i);
                cell.setCellValue(detailHeaders[i]);
                cell.setCellStyle(styles.header());
            }
            int[] detailWidths = {8, 14, 26, 16, 42, 16, 14, 12, 14};
            for (int i = 0; i < detailWidths.length; i++) details.setColumnWidth(i, detailWidths[i] * 256);
            int detailRowNum = 3;
            int detailStt = 1;
            for (PayrollRecord record : records) {
                Employee employee = empMap.get(record.getEmployeeId());
                for (PayrollAdjustment adjustment : adjustmentsByRecord.getOrDefault(record.getId(), List.of())) {
                    Row row = details.createRow(detailRowNum++);
                    writeText(row, 0, String.valueOf(detailStt++), styles.center());
                    writeText(row, 1, employee != null ? employee.getEmployeeCode() : "", styles.text());
                    writeText(row, 2, employee != null ? employee.getFullName() : "", styles.text());
                    writeText(row, 3, adjustmentTypeLabel(adjustment), styles.center());
                    writeText(row, 4, adjustment.getDescription(), styles.text());
                    writeNumber(row, 5, adjustment.getAmount(), adjustment.getType().name().equals("DEDUCTION") ? styles.negativeMoney() : styles.positiveMoney());
                    writeText(row, 6, adjustment.isTaxable() ? "Có" : "Không", styles.center());
                    writeText(row, 7, record.getPayrollMonth(), styles.center());
                    writeText(row, 8, record.getStatus(), styles.center());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Failed to export Excel", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_ERROR");
        }
    }

    private String adjustmentTypeLabel(PayrollAdjustment adjustment) {
        return switch (adjustment.getType()) {
            case BONUS -> "Thưởng";
            case ALLOWANCE -> "Phụ cấp";
            case DEDUCTION -> "Khấu trừ";
        };
    }

    private String columnLetter(int column) {
        StringBuilder result = new StringBuilder();
        int value = column;
        do {
            result.insert(0, (char) ('A' + (value % 26)));
            value = (value / 26) - 1;
        } while (value >= 0);
        return result.toString();
    }

    private void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value.doubleValue() : 0d);
        cell.setCellStyle(style);
    }

    private record ExcelStyles(CellStyle header, CellStyle title, CellStyle meta, CellStyle text, CellStyle center,
                               CellStyle decimal, CellStyle money, CellStyle positiveMoney, CellStyle negativeMoney,
                               CellStyle netMoney, CellStyle totalLabel, CellStyle moneyTotal, CellStyle positiveTotal,
                               CellStyle negativeTotal, CellStyle netTotal) {
        private ExcelStyles(Workbook workbook) {
            this(createHeader(workbook), createTitle(workbook), createMeta(workbook), createText(workbook),
                    createCenter(workbook), createDecimal(workbook), createMoney(workbook, null),
                    createMoney(workbook, IndexedColors.DARK_GREEN), createMoney(workbook, IndexedColors.DARK_RED),
                    createMoney(workbook, IndexedColors.DARK_BLUE), createTotalLabel(workbook),
                    createTotalMoney(workbook, null), createTotalMoney(workbook, IndexedColors.DARK_GREEN),
                    createTotalMoney(workbook, IndexedColors.DARK_RED), createTotalMoney(workbook, IndexedColors.DARK_BLUE));
        }

        private static CellStyle createHeader(Workbook workbook) { CellStyle s = base(workbook); s.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); Font f = workbook.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex()); s.setFont(f); s.setAlignment(HorizontalAlignment.CENTER); return s; }
        private static CellStyle createTitle(Workbook workbook) { CellStyle s = workbook.createCellStyle(); Font f = workbook.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 15); f.setColor(IndexedColors.DARK_BLUE.getIndex()); s.setFont(f); return s; }
        private static CellStyle createMeta(Workbook workbook) { CellStyle s = workbook.createCellStyle(); Font f = workbook.createFont(); f.setItalic(true); f.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); s.setFont(f); return s; }
        private static CellStyle createText(Workbook workbook) { return base(workbook); }
        private static CellStyle createCenter(Workbook workbook) { CellStyle s = base(workbook); s.setAlignment(HorizontalAlignment.CENTER); return s; }
        private static CellStyle createDecimal(Workbook workbook) { CellStyle s = base(workbook); s.setAlignment(HorizontalAlignment.RIGHT); s.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0")); return s; }
        private static CellStyle createMoney(Workbook workbook, IndexedColors color) { CellStyle s = base(workbook); s.setAlignment(HorizontalAlignment.RIGHT); s.setDataFormat(workbook.createDataFormat().getFormat("#,##0;[Red]-#,##0")); if (color != null) { Font f = workbook.createFont(); f.setColor(color.getIndex()); s.setFont(f); } return s; }
        private static CellStyle createTotalLabel(Workbook workbook) { CellStyle s = base(workbook); s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); Font f = workbook.createFont(); f.setBold(true); s.setFont(f); s.setAlignment(HorizontalAlignment.RIGHT); return s; }
        private static CellStyle createTotalMoney(Workbook workbook, IndexedColors color) { CellStyle s = createMoney(workbook, color); s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); Font f = workbook.createFont(); f.setBold(true); if (color != null) f.setColor(color.getIndex()); s.setFont(f); return s; }
        private static CellStyle base(Workbook workbook) { CellStyle s = workbook.createCellStyle(); s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN); s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN); s.setVerticalAlignment(VerticalAlignment.CENTER); return s; }
    }

    /** Kiểm tra STALE */
    public boolean hasStaleRecords(Long companyId, String yearMonth) {
        return payrollRecordRepository.hasStaleRecords(companyId, yearMonth);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<PayrollRecordDto> toDto(List<PayrollRecord> records, Long companyId) {
        Map<Long, Employee> empMap = employeeRepository
                .findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return records.stream()
                .map(r -> toSingleDto(r, empMap.get(r.getEmployeeId())))
                .toList();
    }

    private PayrollRecordDto toSingleDto(PayrollRecord r, Employee emp) {
        List<PayrollAdjustmentDto> adjDtos = null;
        List<PayrollAdjustment> adjs = r.getAdjustments();
        if (adjs == null || adjs.isEmpty()) {
            // Lazy load nếu chưa có
            adjs = adjustmentRepository.findByPayrollRecordIdOrderBySortOrderAsc(r.getId());
        }
        if (adjs != null && !adjs.isEmpty()) {
            adjDtos = adjs.stream().map(this::toAdjustmentDto).toList();
        }

        return new PayrollRecordDto(
                r.getId(),
                r.getEmployeeId(),
                emp != null ? emp.getEmployeeCode() : null,
                emp != null ? emp.getFullName() : null,
                null, // departmentName — enrich via join if needed
                null, // jobRoleName
                r.getPayrollMonth(),
                r.getBaseSalary(),
                r.getDaysWorked(),
                r.getDaysOff(),
                r.getTotalWorkdays(),
                r.getCalculatedSalary(),
                r.getOvertimeAmount(),
                r.getBonus(),
                r.getDeduction(),
                r.getInsuranceSalary(),
                r.getInsuranceEmployeeAmount(),
                r.getInsuranceEmployerAmount(),
                r.getBhxhEmployeeAmount(),
                r.getBhxhEmployerAmount(),
                r.getBhytEmployeeAmount(),
                r.getBhytEmployerAmount(),
                r.getBhtnEmployeeAmount(),
                r.getBhtnEmployerAmount(),
                r.getAdjustmentNote(),
                adjDtos,
                r.getNetSalary(),
                r.getCurrency(),
                r.getStatus(),
                r.getConfirmedBy(),
                r.getConfirmedAt(),
                r.getExportedAt(),
                r.getPaidBy(),
                r.getPaidAt()
        );
    }

    private PayrollAdjustmentDto toAdjustmentDto(PayrollAdjustment a) {
        return new PayrollAdjustmentDto(
                a.getId(), a.getType(), a.getDescription(),
                a.getAmount(), a.isTaxable(), a.getSortOrder());
    }
}
