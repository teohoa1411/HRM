package com.hrms.domain.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.billing.service.CostCalculationService;
import com.hrms.domain.finance.service.FinancialReportService;
import com.hrms.domain.insurance.dto.InsuranceCalculationResult;
import com.hrms.domain.insurance.service.InsuranceService;
import com.hrms.domain.payroll.dto.PayrollAdjustmentDto;
import com.hrms.domain.payroll.dto.PayrollRecordDto;
import com.hrms.domain.payroll.dto.UpdatePayrollRequest;
import com.hrms.domain.payroll.entity.PayrollAdjustment;
import com.hrms.domain.payroll.entity.PayrollRecord;
import com.hrms.domain.payroll.entity.PayrollRecordHistory;
import com.hrms.domain.payroll.repository.PayrollAdjustmentRepository;
import com.hrms.domain.payroll.repository.PayrollRecordHistoryRepository;
import com.hrms.domain.timesheet.repository.TimesheetPeriodLockRepository;
import com.hrms.domain.payroll.repository.PayrollRecordRepository;
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
import java.util.ArrayList;
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
    private final TimesheetPeriodLockRepository timesheetPeriodLockRepository;
    private final FinancialReportService financialReportService;
    private final InsuranceService insuranceService;
    private final CostCalculationService costCalculationService;
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
        log.info("Auto-recalculating billing before payroll generation: company={} month={}", companyId, yearMonth);
        try {
            costCalculationService.calculateForMonth(companyId, yearMonth);
        } catch (Exception e) {
            log.warn("Billing recalc before payroll failed (company={} month={}): {}", companyId, yearMonth, e.getMessage());
        }

        // Bug 1: Dùng ngày đầu tháng bảng lương làm referenceDate cho insurance
        LocalDate insuranceRefDate = payrollYm.atDay(1);

        List<BillingRecord> billings = billingRecordRepository
                .findByCompanyIdAndBillingMonth(companyId, yearMonth)
                .stream()
                .filter(b -> b.getSourceType() == EmployeeSourceType.INTERNAL)
                .toList();

        java.util.Map<Long, java.util.List<BillingRecord>> byEmp = billings.stream()
                .collect(java.util.stream.Collectors.groupingBy(BillingRecord::getEmployeeId));

        List<PayrollRecord> results = new ArrayList<>();
        for (java.util.Map.Entry<Long, java.util.List<BillingRecord>> entry : byEmp.entrySet()) {
            Long empId = entry.getKey();
            java.util.List<BillingRecord> empBillings = entry.getValue();
            BillingRecord first = empBillings.get(0);

            BigDecimal totalDaysWorked = empBillings.stream()
                    .map(BillingRecord::getDaysWorked).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDaysOff = empBillings.stream()
                    .map(BillingRecord::getDaysOff).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = empBillings.stream()
                    .map(BillingRecord::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            PayrollRecord record = payrollRecordRepository
                    .findByCompanyIdAndEmployeeIdAndPayrollMonth(companyId, empId, yearMonth)
                    .orElse(null);

            // Bug 1: Truyền tháng bảng lương thay vì dùng LocalDate.now()
            InsuranceCalculationResult insResult = insuranceService.calculateMonthly(empId, companyId, insuranceRefDate);

            if (record == null) {
                record = PayrollRecord.builder()
                        .companyId(companyId)
                        .employeeId(empId)
                        .billingRecordId(first.getId())
                        .payrollMonth(yearMonth)
                        .baseSalary(first.getUnitCostPrice())
                        .daysWorked(totalDaysWorked)
                        .daysOff(totalDaysOff)
                        .totalWorkdays(BigDecimal.valueOf(first.getTotalWorkdays()))
                        .calculatedSalary(totalCost)
                        .bonus(BigDecimal.ZERO)
                        .deduction(BigDecimal.ZERO)
                        .insuranceSalary(insResult.insuranceSalary())
                        .insuranceEmployeeAmount(insResult.employeeAmount())
                        .insuranceEmployerAmount(insResult.employerAmount())
                        .currency(first.getCurrency())
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
            } else if ("DRAFT".equals(record.getStatus()) || "ADJUSTED".equals(record.getStatus())) {
                record.setBaseSalary(first.getUnitCostPrice());
                record.setDaysWorked(totalDaysWorked);
                record.setDaysOff(totalDaysOff);
                record.setTotalWorkdays(BigDecimal.valueOf(first.getTotalWorkdays()));
                record.setCalculatedSalary(totalCost);
                record.setBillingRecordId(first.getId());
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

    /** Cập nhật bonus/deduction/note hoặc adjustments chi tiết */
    @Transactional
    public PayrollRecordDto updatePayrollRecord(Long companyId, Long id, UpdatePayrollRequest req) {
        PayrollRecord record = payrollRecordRepository.findById(id)
                .filter(r -> r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Payroll record not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!List.of("DRAFT", "ADJUSTED").contains(record.getStatus()) || "PAID".equals(record.getStatus())) {
            throw new BusinessException("Cannot edit confirmed payroll record", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        // Nếu có adjustments → dùng adjustments chi tiết
        if (req.adjustments() != null && !req.adjustments().isEmpty()) {
            // Xóa adjustments cũ và tạo mới
            adjustmentRepository.deleteByPayrollRecordId(record.getId());
            adjustmentRepository.flush();

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
            adjustmentRepository.saveAll(newAdjs);
            record.setAdjustments(newAdjs);
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

        // Tự động cập nhật báo cáo tài chính sau khi confirm lương (chi phí thay đổi)
        try {
            financialReportService.generateMonthlyFinancialRecords(companyId, yearMonth);
        } catch (Exception e) {
            log.warn("Auto-generate financial records after payroll confirm failed: {}", e.getMessage());
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

        Map<Long, Employee> empMap = employeeRepository
                .findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Bang luong " + yearMonth);

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Net salary highlight style
            CellStyle netStyle = wb.createCellStyle();
            netStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            netStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            netStyle.setFont(boldFont);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG LƯƠNG NỘI BỘ - THÁNG " + yearMonth);
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 23));

            // Header row
            String[] headers = {"STT", "Mã NV", "Họ và tên", "Phòng ban", "Vị trí",
                    "Tháng", "Ngày làm", "Ngày nghỉ", "Lương CB",
                    "Lương tính công", "Lương đóng BH",
                    "BHXH NV", "BHYT NV", "BHTN NV", "Tổng BH NV",
                    "BHXH CTy", "BHYT CTy", "BHTN CTy", "Tổng BH CTy",
                    "Thưởng", "Khấu trừ", "Ghi chú KT", "Lương Net", "Trạng thái"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4500);
            }
            sheet.setColumnWidth(2, 6000); // Họ tên
            sheet.setColumnWidth(12, 7000); // Ghi chú

            // Data rows
            int rowNum = 3;
            int stt = 1;
            for (PayrollRecord r : records) {
                Employee emp = empMap.get(r.getEmployeeId());
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(stt++);
                row.createCell(1).setCellValue(emp != null ? emp.getEmployeeCode() : "");
                row.createCell(2).setCellValue(emp != null ? emp.getFullName() : "");
                row.createCell(3).setCellValue(""); // departmentName - cần join
                row.createCell(4).setCellValue(""); // jobRoleName - cần join
                row.createCell(5).setCellValue(r.getPayrollMonth());
                row.createCell(6).setCellValue(r.getDaysWorked().doubleValue());
                row.createCell(7).setCellValue(r.getDaysOff().doubleValue());
                row.createCell(8).setCellValue(r.getBaseSalary().doubleValue());
                row.createCell(9).setCellValue(r.getCalculatedSalary().doubleValue());
                row.createCell(10).setCellValue(r.getInsuranceSalary() != null ? r.getInsuranceSalary().doubleValue() : 0);
                row.createCell(11).setCellValue(r.getBhxhEmployeeAmount() != null ? r.getBhxhEmployeeAmount().doubleValue() : 0);
                row.createCell(12).setCellValue(r.getBhytEmployeeAmount() != null ? r.getBhytEmployeeAmount().doubleValue() : 0);
                row.createCell(13).setCellValue(r.getBhtnEmployeeAmount() != null ? r.getBhtnEmployeeAmount().doubleValue() : 0);
                row.createCell(14).setCellValue(r.getInsuranceEmployeeAmount().doubleValue());
                row.createCell(15).setCellValue(r.getBhxhEmployerAmount() != null ? r.getBhxhEmployerAmount().doubleValue() : 0);
                row.createCell(16).setCellValue(r.getBhytEmployerAmount() != null ? r.getBhytEmployerAmount().doubleValue() : 0);
                row.createCell(17).setCellValue(r.getBhtnEmployerAmount() != null ? r.getBhtnEmployerAmount().doubleValue() : 0);
                row.createCell(18).setCellValue(r.getInsuranceEmployerAmount().doubleValue());
                row.createCell(19).setCellValue(r.getBonus().doubleValue());
                row.createCell(20).setCellValue(r.getDeduction().doubleValue());
                row.createCell(21).setCellValue(r.getAdjustmentNote() != null ? r.getAdjustmentNote() : "");
                Cell netCell = row.createCell(22);
                netCell.setCellValue(r.getNetSalary().doubleValue());
                netCell.setCellStyle(netStyle);
                row.createCell(23).setCellValue(r.getStatus());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Failed to export Excel", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_ERROR");
        }
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
