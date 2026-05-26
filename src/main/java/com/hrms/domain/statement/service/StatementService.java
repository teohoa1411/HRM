package com.hrms.domain.statement.service;

import com.hrms.domain.assignment.entity.EmployeeAssignment;
import com.hrms.domain.assignment.repository.EmployeeAssignmentRepository;
import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.billing.service.CostCalculationService;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.company.entity.Company;
import com.hrms.domain.company.repository.CompanyRepository;
import com.hrms.domain.customer.entity.Customer;
import com.hrms.domain.customer.repository.CustomerRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.internal_statement.entity.InternalStatementEntry;
import com.hrms.domain.internal_statement.repository.InternalStatementEntryRepository;
import com.hrms.domain.jobrole.entity.JobRole;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.domain.statement.dto.StatementDailyDto;
import com.hrms.domain.statement.dto.StatementDto;
import com.hrms.domain.statement.dto.StatementHistoryDto;
import com.hrms.domain.statement.dto.UpdateDailyEntryRequest;
import com.hrms.domain.statement.dto.UpdateLineItemRequest;
import com.hrms.domain.statement.dto.StatementLineItemDto;
import com.hrms.domain.statement.dto.StatementPeriodDto;
import com.hrms.domain.statement.entity.Statement;
import com.hrms.domain.statement.entity.StatementHistory;
import com.hrms.domain.statement.entity.StatementLineItem;
import com.hrms.domain.statement.entity.StatementLineItemDaily;
import com.hrms.domain.statement.repository.StatementHistoryRepository;
import com.hrms.domain.statement.repository.StatementLineItemDailyRepository;
import com.hrms.domain.statement.repository.StatementLineItemRepository;
import com.hrms.domain.statement.repository.StatementRepository;
import com.hrms.domain.timesheet.entity.CompanyTimesheetField;
import com.hrms.domain.timesheet.entity.Timesheet;
import com.hrms.domain.timesheet.entity.TimesheetMonthly;
import com.hrms.domain.timesheet.entity.TimesheetPeriodLock;
import com.hrms.domain.timesheet.repository.CompanyTimesheetFieldRepository;
import com.hrms.domain.timesheet.repository.TimesheetMonthlyRepository;
import com.hrms.domain.timesheet.repository.TimesheetRepository;
import com.hrms.domain.timesheet.repository.TimesheetPeriodLockRepository;
import com.hrms.domain.vendor.entity.Vendor;
import com.hrms.domain.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final StatementHistoryRepository historyRepository;
    private final StatementLineItemRepository lineItemRepository;
    private final StatementLineItemDailyRepository dailyRepository;
    private final InternalStatementEntryRepository internalEntryRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final CostCalculationService costCalculationService;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final VendorRepository vendorRepository;
    private final JobRoleRepository jobRoleRepository;
    private final CompanyRepository companyRepository;
    private final TimesheetPeriodLockRepository timesheetPeriodLockRepository;
    private final TimesheetRepository timesheetRepository;
    private final TimesheetMonthlyRepository timesheetMonthlyRepository;
    private final CompanyTimesheetFieldRepository companyTimesheetFieldRepository;
    private final EmployeeAssignmentRepository assignmentRepository;

    private static final BigDecimal STD_HOURS = new BigDecimal("8.0");

    /**
     * Đảm bảo {@code billing_records} của tháng đã được tính đúng theo
     * {@code employee_assignments} mới nhất TRƯỚC khi tạo biên bản.
     *
     * <p>Lý do: Khi HR tạo/sửa/xoá assignment giữa tháng (vd: assign sangtm1411 sang
     * Techcombank từ 15/04), không có mechanism nào kích hoạt recalc tự động. Nếu kế
     * toán bấm "Tạo lại biên bản" mà bảng {@code billing_records} chưa có row tương
     * ứng, NV đó sẽ KHÔNG lên biên bản KH. Auto-recalc ở đây đảm bảo mọi NV có
     * assignment hợp lệ trong tháng đều xuất hiện trên biên bản tương ứng.</p>
     *
     * <p>Behavior:
     * <ul>
     *   <li>Nếu {@link CostCalculationService#calculateForMonth} fail TOÀN BỘ NV
     *       (signal schema/migration bug) → throw {@link BusinessException} cho user
     *       biết để fix root cause, không silent-fallback dữ liệu cũ.</li>
     *   <li>Nếu lỗi cục bộ (1 vài NV) → log warn và tiếp tục với dữ liệu hiện có.</li>
     * </ul>
     */
    private void ensureBillingRecordsUpToDate(Long companyId, String yearMonth) {
        try {
            int n = costCalculationService.calculateForMonth(companyId, yearMonth);
            log.info("Auto-recalc billing for company={} month={} → {} employees processed",
                    companyId, yearMonth, n);
        } catch (IllegalStateException e) {
            // calculateForMonth chỉ throw IllegalStateException khi ALL employees fail
            // → schema/config bug. Phải surface cho user thay vì âm thầm fallback.
            log.error("Auto-recalc billing FAILED CATASTROPHICALLY company={} month={}: {}",
                    companyId, yearMonth, e.getMessage(), e);
            throw new BusinessException(
                    "Không thể tính lại chi phí cho tháng " + yearMonth
                            + ". Có khả năng schema chưa migrate đúng (V29 period_start/period_end). "
                            + "Chi tiết: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "BILLING_RECALC_FAILED");
        } catch (Exception e) {
            log.warn("Auto-recalc billing partial failure company={} month={}: {} — proceeding with existing data",
                    companyId, yearMonth, e.getMessage());
        }
    }

    /**
     * Bắt buộc: Timesheet tháng phải được Kế toán xác nhận trước khi tạo biên bản.
     * Ngăn việc tạo/ cập nhật biên bản khi timesheet chưa được Kế toán chốt số liệu.
     */
    private void assertNotClosed(Long companyId, String type, Long recipientId, String yearMonth) {
        statementRepository.findByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonth(
                companyId, type, recipientId, yearMonth)
                .ifPresent(s -> {
                    if ("CLOSED".equals(s.getStatus())) {
                        throw new BusinessException(
                                "Biên bản " + type + " tháng " + yearMonth + " đã đóng. Không thể tái tạo.",
                                HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
                    }
                });
    }

    private void assertAccountantConfirmed(Long companyId, String yearMonth) {
        TimesheetPeriodLock lock = timesheetPeriodLockRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseThrow(() -> new BusinessException(
                        "Chưa có lock timesheet tháng " + yearMonth
                                + ". Vui lòng yêu cầu HR khóa và Kế toán xác nhận trước khi tạo biên bản.",
                        HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_LOCKED"));
        if (!lock.isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth
                            + " chưa được Kế toán xác nhận. Vui lòng xác nhận timesheet trước khi tạo biên bản.",
                    HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_ACCOUNTANT_CONFIRMED");
        }
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StatementDto> listCustomerStatements(Long companyId, String yearMonth) {
        return statementRepository
                .findByCompanyIdAndStatementMonthAndStatementType(companyId, yearMonth, "CUSTOMER")
                .stream().map(s -> {
                    try { return toDto(s, false); }
                    catch (Exception e) { log.error("Error mapping statement id={}: {}", s.getId(), e.getMessage(), e); return null; }
                }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public List<StatementDto> listVendorStatements(Long companyId, String yearMonth) {
        return statementRepository
                .findByCompanyIdAndStatementMonthAndStatementType(companyId, yearMonth, "VENDOR")
                .stream().map(s -> {
                    try { return toDto(s, false); }
                    catch (Exception e) { log.error("Error mapping statement id={}: {}", s.getId(), e.getMessage(), e); return null; }
                }).filter(java.util.Objects::nonNull).toList();
    }

    // ── GET DETAIL ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StatementDto getCustomerStatement(Long companyId, Long customerId, String yearMonth) {
        Statement s = findStatement(companyId, "CUSTOMER", customerId, yearMonth);
        return toDto(s, true);
    }

    @Transactional(readOnly = true)
    public StatementDto getVendorStatement(Long companyId, Long vendorId, String yearMonth) {
        Statement s = findStatement(companyId, "VENDOR", vendorId, yearMonth);
        return toDto(s, true);
    }

    // ── GENERATE CUSTOMER ─────────────────────────────────────────────────────

    @Transactional
    public StatementDto generateCustomerStatement(Long companyId, Long customerId, String yearMonth) {
        // Chặn tái tạo khi biên bản đã đóng
        assertNotClosed(companyId, "CUSTOMER", customerId, yearMonth);

        // Pre-check: Kế toán phải xác nhận timesheet trước khi tạo biên bản khách hàng.
        assertAccountantConfirmed(companyId, yearMonth);

        // Auto-sync billing_records với employee_assignments mới nhất.
        // Đảm bảo NV được assign cho KH này (kể cả mid-month) sẽ có BillingRecord → lên biên bản.
        ensureBillingRecordsUpToDate(companyId, yearMonth);

        List<BillingRecord> billings = billingRecordRepository
                .findByCompanyIdAndBillingMonth(companyId, yearMonth)
                .stream().filter(b -> customerId.equals(b.getCustomerId())).toList();

        if (billings.isEmpty()) throw new BusinessException(
                "Không có nhân sự nào được phân công cho khách hàng này trong tháng " + yearMonth
                        + ". Vui lòng kiểm tra lại phân công nhân sự (Assignment).",
                HttpStatus.NOT_FOUND, "NO_BILLING_DATA");

        Statement statement = statementRepository
                .findByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonth(companyId, "CUSTOMER", customerId, yearMonth)
                .orElse(Statement.builder().companyId(companyId).statementType("CUSTOMER")
                        .recipientId(customerId).statementMonth(yearMonth).status("DRAFT").build());

        try {
            Statement saved = buildAndSaveStatement(statement, billings, yearMonth, companyId, "sell");
            return toDto(saved, true);
        } catch (Exception e) {
            log.error("generateCustomerStatement failed companyId={} customerId={} yearMonth={}: {}",
                    companyId, customerId, yearMonth, e.getMessage(), e);
            throw new BusinessException("Lỗi tạo biên bản: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "GENERATE_FAILED");
        }
    }

    // ── GENERATE VENDOR ───────────────────────────────────────────────────────

    @Transactional
    public StatementDto generateVendorStatement(Long companyId, Long vendorId, String yearMonth) {
        // Chặn tái tạo khi biên bản đã đóng
        assertNotClosed(companyId, "VENDOR", vendorId, yearMonth);

        // Pre-check: Kế toán phải xác nhận timesheet trước khi tạo biên bản vendor.
        assertAccountantConfirmed(companyId, yearMonth);

        // Auto-sync billing_records với employee_assignments mới nhất (cùng lý do
        // như generateCustomerStatement: vendor employees có thể được assign mid-month).
        ensureBillingRecordsUpToDate(companyId, yearMonth);

        List<BillingRecord> billings = billingRecordRepository
                .findByCompanyIdAndBillingMonth(companyId, yearMonth)
                .stream().filter(b -> vendorId.equals(b.getVendorId())).toList();

        if (billings.isEmpty()) throw new BusinessException(
                "Không có nhân sự nào thuộc vendor này có chi phí trong tháng " + yearMonth
                        + ". Vui lòng kiểm tra phân công nhân sự và giá vendor.",
                HttpStatus.NOT_FOUND, "NO_BILLING_DATA");

        Statement statement = statementRepository
                .findByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonth(companyId, "VENDOR", vendorId, yearMonth)
                .orElse(Statement.builder().companyId(companyId).statementType("VENDOR")
                        .recipientId(vendorId).statementMonth(yearMonth).status("DRAFT").build());

        Statement saved = buildAndSaveStatement(statement, billings, yearMonth, companyId, "cost");
        return toDto(saved, true);
    }

    private Statement buildAndSaveStatement(Statement statement, List<BillingRecord> billings,
                                            String yearMonth, Long companyId, String priceType) {
        // ─── Aggregate billing records theo employee ────────────────────────────
        // Sau V29, một NV có thể có nhiều BillingRecord trong cùng (recipient, month) nếu
        // có nhiều period (vd: rời rồi quay lại customer). Schema statement_line_items
        // vẫn UNIQUE (statement_id, employee_id) → cần gộp lại 1 line per employee,
        // sum daysWorked. Period chi tiết được giữ trong các billing_records để FE đọc.
        Map<Long, List<BillingRecord>> billingByEmp = billings.stream()
                .collect(Collectors.groupingBy(BillingRecord::getEmployeeId));

        Map<Long, StatementLineItem> existingByEmp = statement.getId() != null
                ? statement.getLineItems().stream().collect(Collectors.toMap(StatementLineItem::getEmployeeId, Function.identity()))
                : new HashMap<>();

        Set<Long> newEmpIds = billingByEmp.keySet();

        // Remove line items for employees no longer in billing.
        // Using removeIf avoids the clear()+addAll() orphan-removal bug in Hibernate.
        if (statement.getId() != null) {
            statement.getLineItems().removeIf(item -> !newEmpIds.contains(item.getEmployeeId()));
        }

        List<StatementLineItem> lineItems = new ArrayList<>();
        for (Map.Entry<Long, List<BillingRecord>> entry : billingByEmp.entrySet()) {
            Long empId = entry.getKey();
            List<BillingRecord> empBillings = entry.getValue();

            // Sum daysWorked across periods (cho cùng customer/vendor)
            BigDecimal totalDays = empBillings.stream()
                    .map(BillingRecord::getDaysWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Lấy giá từ period đầu tiên (giá trong cùng customer cùng tháng thường nhất quán)
            BillingRecord first = empBillings.get(0);
            BigDecimal price = "cost".equals(priceType) ? first.getUnitCostPrice() : first.getUnitSellPrice();
            // Tham chiếu billing_record_id = period đầu (chỉ để truy vết)
            Long refBillingId = first.getId();

            StatementLineItem item = existingByEmp.get(empId);
            if (item == null) {
                item = StatementLineItem.builder()
                        .statement(statement).employeeId(empId)
                        .billingRecordId(refBillingId)
                        .originalDays(totalDays).adjustedDays(totalDays)
                        .unitSellPrice(price).build();
                item.initHoursFromDays();
                statement.getLineItems().add(item);
            } else {
                item.setOriginalDays(totalDays);
                item.setUnitSellPrice(price);
                item.setBillingRecordId(refBillingId);
                BigDecimal hpd = (item.getHoursPerDay() != null && item.getHoursPerDay().compareTo(BigDecimal.ZERO) > 0)
                        ? item.getHoursPerDay() : BigDecimal.valueOf(8.0);
                item.setOriginalHours(totalDays.multiply(hpd));
            }
            item.recalculateAmounts();
            lineItems.add(item);
        }

        statement.setTotalEmployees(lineItems.size());
        statement.setTotalDaysWorked(lineItems.stream().map(StatementLineItem::getAdjustedDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        statement.setTotalAmount(lineItems.stream().map(StatementLineItem::getAdjustedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Always reset to DRAFT on (re-)generation so the workflow restarts
        statement.resetToDraft();

        Statement saved = statementRepository.save(statement);

        // Snapshot the list to avoid ConcurrentModificationException:
        // generateLineItemDailyEntries → recalculateAdjustedDays → statementRepository.save(stmt)
        // would cascade back into the same Hibernate persistent collection we're iterating.
        List<StatementLineItem> lineItemsSnapshot = new ArrayList<>(saved.getLineItems());

        // Generate daily entries for each line item from internal statement
        for (StatementLineItem item : lineItemsSnapshot) {
            try {
                generateLineItemDailyEntries(companyId, item, yearMonth);
            } catch (Exception e) {
                log.warn("Could not generate daily entries for employee {} in {}: {}",
                        item.getEmployeeId(), yearMonth, e.getMessage());
            }
        }

        return saved;
    }

    // ── DAILY CALENDAR ────────────────────────────────────────────────────────

    /** Generate (or re-generate) daily entries for a line item from internal statement */
    @Transactional
    public List<StatementDailyDto> generateLineItemDailyEntries(Long companyId, Long lineItemId, String yearMonth) {
        StatementLineItem checkItem = lineItemRepository.findById(lineItemId).orElse(null);
        if (checkItem != null && checkItem.getStatement().isClosed()) {
            throw new BusinessException("Biên bản đã đóng. Không thể tái tạo dữ liệu ngày.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        StatementLineItem item = lineItemRepository.findById(lineItemId)
                .filter(li -> li.getStatement().getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Line item not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return generateLineItemDailyEntries(companyId, item, yearMonth);
    }

    private List<StatementDailyDto> generateLineItemDailyEntries(Long companyId, StatementLineItem item, String yearMonth) {
        dailyRepository.deleteByStatementLineItemId(item.getId());

        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        Statement stmt = item.getStatement();
        Long recipientId = stmt != null ? stmt.getRecipientId() : null;
        String stmtType  = stmt != null ? stmt.getStatementType() : null;

        // ─── 1. Lấy giờ TIMESHEET RAW (nguồn chính) — sum hours+otHours theo ngày ────
        Employee emp = employeeRepository.findById(item.getEmployeeId()).orElse(null);
        Long userId = emp != null ? emp.getUserId() : null;

        Map<LocalDate, BigDecimal> timesheetByDate = new HashMap<>();
        if (userId != null) {
            List<Timesheet> entries = timesheetRepository.findByUserAndMonth(userId, companyId, monthStart, monthEnd);
            for (Timesheet t : entries) {
                BigDecimal h = t.getHours() != null ? t.getHours() : BigDecimal.ZERO;
                BigDecimal ot = t.getOtHours() != null ? t.getOtHours() : BigDecimal.ZERO;
                timesheetByDate.merge(t.getWorkDate(), h.add(ot), BigDecimal::add);
            }
        }

        // ─── 2. Lấy InternalStatementEntry CHỈ để hiển thị leave info ─────────────
        List<InternalStatementEntry> internalEntries = internalEntryRepository
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, item.getEmployeeId(), yearMonth);
        Map<LocalDate, InternalStatementEntry> internalByDate = internalEntries.stream()
                .collect(Collectors.toMap(InternalStatementEntry::getWorkDate, Function.identity()));

        // ─── 3. Lấy periods của recipient cho NV này → chỉ generate daily trong period ──
        // Mỗi BillingRecord thuộc statement này chính là 1 period. Cần lọc theo recipient
        // (customer_id hoặc vendor_id) vì NV có thể đã làm cho recipient khác trong tháng.
        List<BillingRecord> empRecords = billingRecordRepository
                .findByCompanyAndEmployeeAndMonth(companyId, item.getEmployeeId(), yearMonth);
        List<BillingRecord> recipientPeriods = empRecords.stream()
                .filter(b -> matchesRecipient(b, stmtType, recipientId))
                .toList();

        // ─── 4. Tạo daily entries: chỉ cho ngày THUỘC ÍT NHẤT 1 period của recipient ─
        List<StatementLineItemDaily> dailies = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            if (!isInAnyPeriod(date, recipientPeriods)) continue; // ngày ngoài period → skip

            BigDecimal tsHours = timesheetByDate.getOrDefault(date, BigDecimal.ZERO);
            InternalStatementEntry internal = internalByDate.get(date);
            boolean hasTimesheet = tsHours.compareTo(BigDecimal.ZERO) > 0;
            boolean hasLeave = internal != null && internal.isLeaveDay();

            if (!hasTimesheet && !hasLeave) continue;

            StatementLineItemDaily daily = StatementLineItemDaily.builder()
                    .statementLineItemId(item.getId())
                    .companyId(companyId)
                    .employeeId(item.getEmployeeId())
                    .workDate(date)
                    .internalHours(tsHours)
                    .customerHours(tsHours)
                    .build();
            dailies.add(daily);
        }

        dailyRepository.saveAll(dailies);
        recalculateAdjustedDays(item, dailies);

        return buildDailyCalendar(yearMonth, dailies, internalByDate);
    }

    /** Một BillingRecord có khớp recipient (customer/vendor) của statement không. */
    private boolean matchesRecipient(BillingRecord b, String stmtType, Long recipientId) {
        if (recipientId == null || stmtType == null) return false;
        if ("CUSTOMER".equals(stmtType)) return recipientId.equals(b.getCustomerId());
        if ("VENDOR".equals(stmtType))   return recipientId.equals(b.getVendorId());
        return false;
    }

    /** Ngày có nằm trong ít nhất một period không. */
    private boolean isInAnyPeriod(LocalDate date, List<BillingRecord> periods) {
        for (BillingRecord b : periods) {
            if (!date.isBefore(b.getPeriodStart()) && !date.isAfter(b.getPeriodEnd())) {
                return true;
            }
        }
        return false;
    }

    /** Get full month calendar for a line item */
    @Transactional(readOnly = true)
    public List<StatementDailyDto> getLineItemDailyCalendar(Long companyId, Long lineItemId, String yearMonth) {
        StatementLineItem item = lineItemRepository.findById(lineItemId)
                .filter(li -> li.getStatement().getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Line item not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        List<StatementLineItemDaily> dailies = dailyRepository.findByStatementLineItemId(lineItemId);

        List<InternalStatementEntry> internalEntries = internalEntryRepository
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, item.getEmployeeId(), yearMonth);
        Map<LocalDate, InternalStatementEntry> internalByDate = internalEntries.stream()
                .collect(Collectors.toMap(InternalStatementEntry::getWorkDate, Function.identity()));

        // If no daily entries exist yet, auto-generate them
        if (dailies.isEmpty()) {
            return generateLineItemDailyEntries(companyId, item, yearMonth);
        }

        return buildDailyCalendar(yearMonth, dailies, internalByDate);
    }

    /** Accountant edits hours for a specific day */
    @Transactional
    public StatementDailyDto updateLineItemDailyEntry(Long companyId, Long lineItemId,
                                                      LocalDate date, UpdateDailyEntryRequest req) {
        StatementLineItem item = lineItemRepository.findById(lineItemId)
                .filter(li -> li.getStatement().getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Line item not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Chặn sửa khi biên bản đã đóng
        if (item.getStatement().isClosed()) {
            throw new BusinessException("Biên bản đã đóng. Không thể chỉnh sửa.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        StatementLineItemDaily daily = dailyRepository
                .findByStatementLineItemIdAndWorkDate(lineItemId, date)
                .orElseGet(() -> StatementLineItemDaily.builder()
                        .statementLineItemId(lineItemId).companyId(companyId)
                        .employeeId(item.getEmployeeId()).workDate(date)
                        .internalHours(BigDecimal.ZERO).customerHours(BigDecimal.ZERO).build());

        if (req.hours() != null) daily.setCustomerHours(req.hours());
        if (req.note() != null) daily.setAccountantNote(req.note());
        dailyRepository.save(daily);

        // Recalculate adjustedDays on line item
        List<StatementLineItemDaily> allDailies = dailyRepository.findByStatementLineItemId(lineItemId);
        recalculateAdjustedDays(item, allDailies);

        InternalStatementEntry internal = internalEntryRepository
                .findByCompanyIdAndEmployeeIdAndWorkDate(companyId, item.getEmployeeId(), date)
                .orElse(null);

        return toDailyDto(daily, internal);
    }

    private void recalculateAdjustedDays(StatementLineItem item, List<StatementLineItemDaily> dailies) {
        BigDecimal totalHours = dailies.stream()
                .map(StatementLineItemDaily::getCustomerHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal adjustedDays = totalHours.divide(STD_HOURS, 2, RoundingMode.HALF_UP);
        item.setAdjustedDays(adjustedDays);
        item.recalculateAmounts();
        lineItemRepository.save(item);

        // Update statement totals
        Statement stmt = item.getStatement();
        if (stmt != null) {
            BigDecimal totalDays = stmt.getLineItems().stream()
                    .map(StatementLineItem::getAdjustedDays).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAmount = stmt.getLineItems().stream()
                    .map(StatementLineItem::getAdjustedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            stmt.setTotalDaysWorked(totalDays);
            stmt.setTotalAmount(totalAmount);
            statementRepository.save(stmt);
        }
    }

    private List<StatementDailyDto> buildDailyCalendar(String yearMonth,
                                                        List<StatementLineItemDaily> dailies,
                                                        Map<LocalDate, InternalStatementEntry> internalByDate) {
        Map<LocalDate, StatementLineItemDaily> dailyByDate = dailies.stream()
                .collect(Collectors.toMap(StatementLineItemDaily::getWorkDate, Function.identity()));

        YearMonth ym = YearMonth.parse(yearMonth);
        List<StatementDailyDto> result = new ArrayList<>();

        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            StatementLineItemDaily daily = dailyByDate.get(date);
            InternalStatementEntry internal = internalByDate.get(date);

            // Show all days that have either internal data or a daily entry
            if (daily == null && internal == null) {
                // Only skip truly empty days (no work, no leave)
                String dow = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
                boolean weekend = date.getDayOfWeek().getValue() >= 6;
                result.add(new StatementDailyDto(null, date, dow, weekend,
                        BigDecimal.ZERO, BigDecimal.ZERO, false, false, false, null, null));
            } else {
                result.add(toDailyDto(daily, internal));
            }
        }
        return result;
    }

    private StatementDailyDto toDailyDto(StatementLineItemDaily daily, InternalStatementEntry internal) {
        LocalDate date = daily != null ? daily.getWorkDate() : (internal != null ? internal.getWorkDate() : null);
        if (date == null) return null;

        String dow = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        boolean weekend = date.getDayOfWeek().getValue() >= 6;

        BigDecimal internalHours = daily != null ? daily.getInternalHours() :
                (internal != null ? internal.getAdjustedHours() : BigDecimal.ZERO);
        BigDecimal customerHours = daily != null ? daily.getCustomerHours() : internalHours;

        boolean leaveDay = internal != null && internal.isLeaveDay();
        boolean halfLeave = internal != null && internal.isHalfLeave();
        boolean paidLeave = internal != null && internal.isPaidLeave();
        String leaveTypeName = internal != null ? internal.getLeaveTypeName() : null;

        return new StatementDailyDto(
                daily != null ? daily.getId() : null,
                date, dow, weekend,
                internalHours, customerHours,
                leaveDay, halfLeave, paidLeave, leaveTypeName,
                daily != null ? daily.getAccountantNote() : null
        );
    }

    // ── UPDATE LINE ITEM (summary level) ──────────────────────────────────────

    @Transactional
    public StatementLineItemDto updateLineItem(Long companyId, Long lineItemId, UpdateLineItemRequest req) {
        StatementLineItem item = lineItemRepository.findById(lineItemId)
                .filter(li -> li.getStatement().getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException("Line item not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!item.getStatement().isEditable()) {
            throw new BusinessException("Cannot edit statement in status: " + item.getStatement().getStatus(),
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        // Ưu tiên điều chỉnh theo giờ nếu có
        if (req.adjustedHours() != null) {
            item.setAdjustedHours(req.adjustedHours());
            item.recalculateAmounts(); // tự cập nhật adjustedDays từ hours
        } else if (req.adjustedDays() != null) {
            // Fallback: điều chỉnh theo ngày → convert sang giờ
            item.setAdjustedDays(req.adjustedDays());
            item.initHoursFromDays(); // sync hours từ days mới
            item.recalculateAmounts();
        }
        if (req.note() != null) item.setNote(req.note());

        StatementLineItem saved = lineItemRepository.save(item);

        // Cập nhật tổng statement
        Statement stmt = saved.getStatement();
        stmt.setTotalDaysWorked(stmt.getLineItems().stream()
                .map(StatementLineItem::getAdjustedDays).reduce(BigDecimal.ZERO, BigDecimal::add));
        stmt.setTotalAmount(stmt.getLineItems().stream()
                .map(StatementLineItem::getAdjustedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        statementRepository.save(stmt);

        return toLineItemDto(saved, null);
    }

    // ── STATUS TRANSITIONS ────────────────────────────────────────────────────

    @Transactional
    public StatementDto markAsSent(Long companyId, String type, Long recipientId, String yearMonth, Long sentBy) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        String prevStatus = statement.getStatus();
        statement.markSent(sentBy);
        statementRepository.save(statement);
        saveHistory(statement.getId(), prevStatus, "SENT", sentBy, "Đã gửi cho đối tác");
        return toDto(statement, false);
    }

    @Transactional
    public StatementDto markAsExported(Long companyId, String type, Long recipientId,
                                        String yearMonth, Long exportedBy, String s3Key) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        String prevStatus = statement.getStatus();
        statement.markExported(s3Key, exportedBy);
        statementRepository.save(statement);
        saveHistory(statement.getId(), prevStatus, "EXPORTED", exportedBy, "Đã xuất file");
        return toDto(statement, false);
    }

    @Transactional
    public StatementDto markAsConfirmed(Long companyId, String type, Long recipientId,
                                         String yearMonth, Long confirmedBy) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        if (!"SENT".equals(statement.getStatus())) {
            throw new BusinessException("Chỉ có thể xác nhận biên bản đã gửi (SENT)", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        String prevStatus = statement.getStatus();
        statement.markConfirmed(confirmedBy);
        statementRepository.save(statement);
        saveHistory(statement.getId(), prevStatus, "CONFIRMED", confirmedBy, "Đối tác xác nhận");
        return toDto(statement, false);
    }

    @Transactional
    public StatementDto resetToDraft(Long companyId, String type, Long recipientId,
                                      String yearMonth, Long resetBy) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        if (!statement.isStale()) {
            throw new BusinessException("Chỉ có thể reset biên bản đang STALE", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        String prevStatus = statement.getStatus();
        statement.resetToDraft();
        statementRepository.save(statement);
        saveHistory(statement.getId(), prevStatus, "DRAFT", resetBy, "Reset về DRAFT sau khi timesheet bị chỉnh");
        return toDto(statement, false);
    }

    /**
     * Đóng biên bản — khóa timesheet + payroll + internal statement.
     * Chỉ COMPANY_ADMIN hoặc ACCOUNTANT gọi được.
     */
    @Transactional
    public StatementDto closeStatement(Long companyId, String type, Long recipientId,
                                       String yearMonth, Long closedBy, String reason) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        if (!"CONFIRMED".equals(statement.getStatus())) {
            throw new BusinessException("Chỉ có thể đóng biên bản đã CONFIRMED", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        String prevStatus = statement.getStatus();
        statement.markClosed(closedBy);
        statementRepository.save(statement);

        // Khóa timesheet tháng này
        lockTimesheetForClosed(companyId, yearMonth, closedBy, reason);

        saveHistory(statement.getId(), prevStatus, "CLOSED", closedBy,
                reason != null ? reason : "Đóng biên bản sau thanh toán");

        log.info("Statement CLOSED: type={}, recipientId={}, month={}, by={}",
                type, recipientId, yearMonth, closedBy);

        return toDto(statement, false);
    }

    /**
     * Mở lại biên bản — chỉ COMPANY_ADMIN.
     * Bắt buộc nhập lý do.
     */
    @Transactional
    public StatementDto reopenStatement(Long companyId, String type, Long recipientId,
                                        String yearMonth, Long reopenBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Phải nhập lý do mở lại biên bản", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }

        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        if (!"CLOSED".equals(statement.getStatus())) {
            throw new BusinessException("Chỉ có thể mở lại biên bản đã CLOSED", HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        String prevStatus = statement.getStatus();
        statement.reopen();
        statementRepository.save(statement);

        // Mở khóa timesheet
        unlockTimesheetFromClosed(companyId, yearMonth, reason);

        saveHistory(statement.getId(), prevStatus, "CONFIRMED", reopenBy,
                "Mở lại biên bản: " + reason);

        log.info("Statement REOPENED: type={}, recipientId={}, month={}, by={}, reason={}",
                type, recipientId, yearMonth, reopenBy, reason);

        return toDto(statement, false);
    }

    private void lockTimesheetForClosed(Long companyId, String yearMonth, Long closedBy, String reason) {
        TimesheetPeriodLock lock = timesheetPeriodLockRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseGet(() -> TimesheetPeriodLock.builder()
                        .companyId(companyId)
                        .yearMonth(yearMonth)
                        .build());

        lock.setStatementClosed(true);
        lock.setClosedBy(closedBy);
        lock.setClosedAt(LocalDateTime.now());
        lock.setClosedReason(reason);
        lock.setReopenReason(null);
        timesheetPeriodLockRepository.save(lock);
    }

    private void unlockTimesheetFromClosed(Long companyId, String yearMonth, String reason) {
        timesheetPeriodLockRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .ifPresent(lock -> {
                    lock.setStatementClosed(false);
                    lock.setClosedBy(null);
                    lock.setClosedAt(null);
                    lock.setReopenReason(reason);
                    timesheetPeriodLockRepository.save(lock);
                });
    }

    @Transactional(readOnly = true)
    public List<StatementHistoryDto> getHistory(Long companyId, String type, Long recipientId, String yearMonth) {
        Statement statement = findStatement(companyId, type, recipientId, yearMonth);
        return historyRepository.findByStatementIdOrderByChangedAtDesc(statement.getId())
                .stream().map(h -> new StatementHistoryDto(h.getId(), h.getStatementId(),
                        h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                        h.getChangedAt(), h.getChangeReason()))
                .toList();
    }

    private void saveHistory(Long statementId, String from, String to, Long changedBy, String reason) {
        historyRepository.save(StatementHistory.transition(statementId, from, to, changedBy, reason));
    }

    // ── EXCEL EXPORT ──────────────────────────────────────────────────────────

    @Transactional
    public byte[] exportCustomerStatementExcel(Long companyId, Long customerId, String yearMonth, Long exportedBy) {
        Statement s = findStatement(companyId, "CUSTOMER", customerId, yearMonth);
        byte[] data = buildBienbancExcel(s, companyId, "CUSTOMER", yearMonth);
        // Mark as exported when in DRAFT or STALE; re-export when already EXPORTED is also fine
        if (s.isEditable()) {
            String prevStatus = s.getStatus();
            s.markExported(null, exportedBy);
            statementRepository.save(s);
            saveHistory(s.getId(), prevStatus, "EXPORTED", exportedBy, "Xuất biên bản Excel");
        }
        return data;
    }

    @Transactional
    public byte[] exportVendorStatementExcel(Long companyId, Long vendorId, String yearMonth, Long exportedBy) {
        Statement s = findStatement(companyId, "VENDOR", vendorId, yearMonth);
        byte[] data = buildBienbancExcel(s, companyId, "VENDOR", yearMonth);
        if (s.isEditable()) {
            String prevStatus = s.getStatus();
            s.markExported(null, exportedBy);
            statementRepository.save(s);
            saveHistory(s.getId(), prevStatus, "EXPORTED", exportedBy, "Xuất biên bản Excel");
        }
        return data;
    }

    private byte[] buildBienbancExcel(Statement statement, Long companyId, String type, String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);

        // Column indices — customer-facing timesheet format
        final int C_NO = 0, C_VENDOR = 1, C_NAME = 2, C_ROLE = 3, C_PROJECT = 4; // E: Project stores projectId
        final int C_KEY = 5, C_TCBUSER = 6, C_LOCATION = 7, C_ONBOARD = 8;
        final int C_DAY = 9; // days: C_DAY to C_DAY+daysInMonth-1
        final int C_TOTAL_H   = C_DAY + daysInMonth;
        final int C_MANDAY    = C_TOTAL_H + 1;
        final int C_RATE      = C_MANDAY + 1;       // Hourly Rate (VND)
        final int C_TOTAL_AMT = C_RATE + 1;         // Total (VND)
        final int C_OT_STATUS = C_TOTAL_AMT + 1;    // OT Status (100% = thường, 150% = OT)

        // Load custom fields (ordered by id ASC = thứ tự tăng dần trong DB)
        List<CompanyTimesheetField> customFields =
                companyTimesheetFieldRepository.findByCompanyIdAndActiveTrueOrderByIdAsc(companyId);
        int numCustomFields = customFields.size();

        // Load TimesheetMonthly (description + additions) for all employees this month
        Map<Long, TimesheetMonthly> monthlyMetaByUserId =
                timesheetMonthlyRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                        .stream().collect(Collectors.toMap(TimesheetMonthly::getUserId, Function.identity()));

        // Dynamic columns after OT Status
        final int C_CUSTOM_START = C_OT_STATUS + 1;    // custom field cols start here
        final int C_NOTE         = C_CUSTOM_START + numCustomFields; // Mô tả timesheet
        final int C_APPROVE_TS   = C_NOTE + 1;         // Approve TS (Hour)
        final int C_INOUT        = C_APPROVE_TS + 1;   // In out (manual)
        final int C_GIAI_TRINH   = C_INOUT + 1;        // Giải trình
        final int TOTAL_COLS     = C_GIAI_TRINH + 1;

        // Load reference data
        Map<Long, Employee> empMap = employeeRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, JobRole> roleMap = jobRoleRepository.findByCompanyIdAndDeletedAtIsNull(
                        companyId, org.springframework.data.domain.Pageable.unpaged())
                .stream().collect(Collectors.toMap(JobRole::getId, Function.identity()));

        // Load EmployeeAssignment for Project name & Key (one per employee — most recent)
        Set<Long> lineItemEmpIds = statement.getLineItems().stream()
                .map(StatementLineItem::getEmployeeId).collect(Collectors.toSet());
        Map<Long, EmployeeAssignment> assignMap = new HashMap<>();
        if (!lineItemEmpIds.isEmpty() && "CUSTOMER".equals(type)) {
            assignmentRepository.findByCustomerAndEmployeesInMonth(
                            statement.getRecipientId(), lineItemEmpIds, firstDay, ym.atEndOfMonth())
                    .forEach(a -> assignMap.putIfAbsent(a.getEmployeeId(), a));
        }

        // Daily hours: lineItemId → date → customerHours
        Map<Long, Map<LocalDate, BigDecimal>> dailyByItem = new HashMap<>();
        for (StatementLineItem li : statement.getLineItems()) {
            Map<LocalDate, BigDecimal> dateMap = dailyRepository.findByStatementLineItemId(li.getId())
                    .stream().collect(Collectors.toMap(
                            StatementLineItemDaily::getWorkDate,
                            StatementLineItemDaily::getCustomerHours));
            dailyByItem.put(li.getId(), dateMap);
        }

        // Company & recipient names
        String ourCompanyName = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .map(Company::getName).orElse("CÔNG TY AGP");
        // Vendor code: mã vendor do khách hàng cấp cho công ty mình (chỉ có khi type=CUSTOMER)
        String vendorCodeForExcel = "CUSTOMER".equals(type)
                ? customerRepository.findById(statement.getRecipientId())
                        .map(c -> c.getVendorCode() != null ? c.getVendorCode() : "")
                        .orElse("")
                : "";
        String recipientName = "CUSTOMER".equals(type)
                ? customerRepository.findById(statement.getRecipientId()).map(Customer::getName).orElse("")
                : vendorRepository.findById(statement.getRecipientId()).map(Vendor::getName).orElse("");

        // Month label: "Tháng 03/2026"
        String monthLabel = "Tháng " + String.format("%02d/%04d", ym.getMonthValue(), ym.getYear());

        // Day-of-week abbreviations (Sun=0, Mon=1, ..., Sat=6)
        String[] DOW = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // ── Styles (match template: Times New Roman 9pt, gray header) ────
            XSSFCellStyle centerBold = centerBoldStyle(wb);
            XSSFCellStyle colHeader  = colHeaderStyle(wb);
            XSSFCellStyle dayHeader  = dayHeaderStyle(wb);
            XSSFCellStyle sunStyle   = sunColumnStyle(wb);
            XSSFCellStyle satStyle   = satColumnStyle(wb);
            XSSFCellStyle numStyle   = numberStyle(wb);
            XSSFCellStyle amtStyle   = amountStyle(wb);
            XSSFCellStyle totalStyle = totalRowStyle(wb);
            XSSFCellStyle wrapStyle  = wrapTextStyle(wb);
            XSSFCellStyle borderStyle = borderCellStyle(wb);

            XSSFSheet sheet = wb.createSheet("TS T" + ym.getMonthValue());
            sheet.setDefaultRowHeightInPoints(15.75f);

            // ── Column widths (match template exactly) ────────────────────────
            sheet.setColumnWidth(C_NO,       (int)(7.33 * 256));   // A: No.
            sheet.setColumnWidth(C_VENDOR,   (int)(13.0 * 256));   // B: Vendor
            sheet.setColumnWidth(C_NAME,     (int)(20.16 * 256));  // C: Name
            sheet.setColumnWidth(C_ROLE,     (int)(25.83 * 256));  // D: Role
            sheet.setColumnWidth(C_PROJECT,  (int)(14.0 * 256));   // E: Project (projectId)
            sheet.setColumnWidth(C_KEY,      (int)(19.0 * 256));   // F: Key
            sheet.setColumnWidth(C_TCBUSER,  (int)(10.5 * 256));   // G: TCB user
            sheet.setColumnWidth(C_LOCATION, (int)(8.33 * 256));   // H: Location
            sheet.setColumnWidth(C_ONBOARD,  (int)(10.5 * 256));   // I: Onboard Date
            // Day columns: Sunday = narrow (5.83), others = 5.83
            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate date = ym.atDay(d);
                int dow = date.getDayOfWeek().getValue() % 7;
                boolean isSun = (dow == 0);
                sheet.setColumnWidth(C_DAY + d - 1, (int)((isSun ? 5.83 : 5.83) * 256));
            }
            // Summary columns
            sheet.setColumnWidth(C_TOTAL_H,   (int)(11.16 * 256));
            sheet.setColumnWidth(C_MANDAY,    (int)(10.66 * 256));
            sheet.setColumnWidth(C_RATE,      (int)(13.0 * 256));
            sheet.setColumnWidth(C_TOTAL_AMT, (int)(13.25 * 256));
            sheet.setColumnWidth(C_OT_STATUS, (int)(10.66 * 256));
            // Custom field columns
            for (int i = 0; i < numCustomFields; i++) {
                sheet.setColumnWidth(C_CUSTOM_START + i, (int)(12.0 * 256));
            }
            sheet.setColumnWidth(C_NOTE,      (int)(56.33 * 256));
            sheet.setColumnWidth(C_APPROVE_TS,(int)(9.16 * 256));
            sheet.setColumnWidth(C_INOUT,     (int)(8.5 * 256));
            sheet.setColumnWidth(C_GIAI_TRINH,(int)(49.83 * 256));

            // ── Rows 0-3: Titles, Row 4: empty ───────────────────────────────
            int mergeEnd = C_TOTAL_AMT; // merge A to AR
            setMergedCenteredBold(sheet, 0, 0, mergeEnd, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", wb, 11);
            setMergedCenteredBold(sheet, 1, 0, mergeEnd, "Độc Lập- Tự do- Hạnh phúc", wb, 11);
            setMergedCenteredBold(sheet, 2, 0, mergeEnd,
                    "BẢNG XÁC NHẬN THỜI GIAN THỰC HIỆN VÀ CHI PHÍ PHÁT TRIỂN PHẦN MỀM", wb, 13);
            setMergedCenteredBold(sheet, 3, 0, mergeEnd, monthLabel, wb, 11);
            for (int r = 0; r <= 3; r++) sheet.getRow(r).setHeightInPoints(24);
            sheet.createRow(4).setHeightInPoints(24); // empty row

            // ── Row 5: Column headers (merged with row 6 for non-day cols) ───
            final int HDR_ROW = 5;
            final int DAY_NUM_ROW = 6;
            Row hRow = sheet.createRow(HDR_ROW);
            hRow.setHeightInPoints(15.75f);
            Row dRow = sheet.createRow(DAY_NUM_ROW);
            dRow.setHeightInPoints(15.75f);

            // Non-day header cells (merged across rows 5-6)
            // Static cols before day columns
            int[] staticCols = {C_NO, C_VENDOR, C_NAME, C_ROLE, C_PROJECT, C_KEY,
                    C_TCBUSER, C_LOCATION, C_ONBOARD};
            String[] staticLabels = {"No.", "Vendor", "Name", "Role", "Project", "Key",
                    "TCB user", "Location", "Onboard Date"};
            for (int i = 0; i < staticCols.length; i++) {
                setCell(hRow, staticCols[i], staticLabels[i], colHeader);
                setCell(dRow, staticCols[i], "", colHeader);
                sheet.addMergedRegion(new CellRangeAddress(HDR_ROW, DAY_NUM_ROW, staticCols[i], staticCols[i]));
            }
            // Static summary cols
            int[] summaryCols = {C_TOTAL_H, C_MANDAY, C_RATE, C_TOTAL_AMT, C_OT_STATUS};
            String[] summaryLabels = {"Total BCC hour", "Total Manday", "Hourly Rate\n (VND)", "Total\n (VND)", "OT Status"};
            for (int i = 0; i < summaryCols.length; i++) {
                setCell(hRow, summaryCols[i], summaryLabels[i], colHeader);
                setCell(dRow, summaryCols[i], "", colHeader);
                sheet.addMergedRegion(new CellRangeAddress(HDR_ROW, DAY_NUM_ROW, summaryCols[i], summaryCols[i]));
            }
            // Dynamic custom field cols
            for (int i = 0; i < numCustomFields; i++) {
                int col = C_CUSTOM_START + i;
                setCell(hRow, col, customFields.get(i).getFieldName(), colHeader);
                setCell(dRow, col, "", colHeader);
                sheet.addMergedRegion(new CellRangeAddress(HDR_ROW, DAY_NUM_ROW, col, col));
            }
            // Trailing static cols
            int[] trailingCols = {C_NOTE, C_APPROVE_TS, C_INOUT, C_GIAI_TRINH};
            String[] trailingLabels = {
                    "Note: các ngày wfh với evidence/ mượn thẻ /...",
                    "Approve TS (Hour)", "In out", "Giải trình"};
            for (int i = 0; i < trailingCols.length; i++) {
                setCell(hRow, trailingCols[i], trailingLabels[i], colHeader);
                setCell(dRow, trailingCols[i], "", colHeader);
                sheet.addMergedRegion(new CellRangeAddress(HDR_ROW, DAY_NUM_ROW, trailingCols[i], trailingCols[i]));
            }

            // Day-of-week headers (row 5) + day numbers (row 6)
            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate date = ym.atDay(d);
                int dow = date.getDayOfWeek().getValue() % 7;
                boolean isSun = (dow == 0), isSat = (dow == 6);
                XSSFCellStyle dStyle = isSun ? sunStyle : (isSat ? satStyle : dayHeader);

                setCell(hRow, C_DAY + d - 1, DOW[dow], dStyle);

                XSSFCell dayCell = (XSSFCell) dRow.createCell(C_DAY + d - 1);
                dayCell.setCellValue(d);
                dayCell.setCellStyle(dStyle);
            }

            // ── Data rows (start at row 7) ────────────────────────────────────
            int dataRowStart = 7;
            int rowNum = dataRowStart;
            int stt = 1;

            // Collect OT data to render after all regular rows
            // Each entry: [Employee, JobRole, EmployeeAssignment, projectKey, dayOtMap, StatementLineItem]
            record OtRowData(Employee emp, JobRole role, EmployeeAssignment assign,
                             String projectKey, String projectId, String projectName,
                             Map<LocalDate, BigDecimal> dayOtHours,
                             StatementLineItem item) {}
            List<OtRowData> otRows = new ArrayList<>();

            DateTimeFormatter ddMMyyyy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            for (StatementLineItem item : statement.getLineItems()) {
                Employee emp = empMap.get(item.getEmployeeId());
                JobRole role = (emp != null && emp.getJobRoleId() != null) ? roleMap.get(emp.getJobRoleId()) : null;
                EmployeeAssignment assign = assignMap.get(item.getEmployeeId());

                // Hourly rate (same for all project rows of this employee)
                BigDecimal hpd = item.getHoursPerDay() != null && item.getHoursPerDay().compareTo(BigDecimal.ZERO) > 0
                        ? item.getHoursPerDay() : BigDecimal.valueOf(8);
                BigDecimal hourlyRate = item.getUnitSellPrice() != null
                        ? item.getUnitSellPrice().divide(hpd, 0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                String onboardStr = emp != null && emp.getStartDate() != null
                        ? emp.getStartDate().format(ddMMyyyy) : "";
                String empName  = emp  != null ? emp.getFullName()  : "";
                String roleName = role != null ? role.getName()     : "";
                String tcbUser  = assign != null && assign.getClientUsername() != null ? assign.getClientUsername() : "";
                String location = assign != null && assign.getWorkLocation()   != null ? assign.getWorkLocation()   : "";

                // Load raw timesheet entries for this employee
                List<Timesheet> tsEntries = Collections.emptyList();
                if (emp != null && emp.getUserId() != null) {
                    tsEntries = timesheetRepository.findByUserAndMonth(
                            emp.getUserId(), companyId, firstDay, ym.atEndOfMonth());
                }

                // Group by projectKey (preserve insertion order → sorted by projectKey from repo)
                Map<String, Map<LocalDate, BigDecimal>> regularByProject  = new LinkedHashMap<>();
                Map<String, Map<LocalDate, BigDecimal>> otByProject       = new LinkedHashMap<>();
                // projectKey → projectName / projectId (first non-null wins)
                Map<String, String> projectNameByKey = new LinkedHashMap<>();
                Map<String, String> projectIdByKey   = new LinkedHashMap<>();

                for (Timesheet t : tsEntries) {
                    String pk = t.getProjectKey();
                    projectNameByKey.putIfAbsent(pk, t.getProjectName() != null ? t.getProjectName() : "");
                    projectIdByKey.putIfAbsent(pk, t.getProjectId() != null ? t.getProjectId() : "");
                    if (t.getHours() != null && t.getHours().compareTo(BigDecimal.ZERO) > 0) {
                        regularByProject.computeIfAbsent(pk, k -> new LinkedHashMap<>())
                                .merge(t.getWorkDate(), t.getHours(), BigDecimal::add);
                    }
                    if (t.getOtHours() != null && t.getOtHours().compareTo(BigDecimal.ZERO) > 0) {
                        otByProject.computeIfAbsent(pk, k -> new LinkedHashMap<>())
                                .merge(t.getWorkDate(), t.getOtHours(), BigDecimal::add);
                    }
                }

                // If no timesheet data, fall back to dailyCalendar customerHours (single row)
                if (regularByProject.isEmpty()) {
                    Map<LocalDate, BigDecimal> dayHours = dailyByItem.getOrDefault(item.getId(), Collections.emptyMap());
                    regularByProject.put("", dayHours);
                    projectNameByKey.put("", "");
                }

                boolean firstProjectRow = true;
                for (Map.Entry<String, Map<LocalDate, BigDecimal>> pe : regularByProject.entrySet()) {
                    String projKey = pe.getKey();
                    Map<LocalDate, BigDecimal> projDayH = pe.getValue();

                    Row row = sheet.createRow(rowNum++);
                    row.setHeightInPoints(15);

                    String projId = projectIdByKey.getOrDefault(projKey, "");
                    setCell(row, C_NO,       stt++,      numStyle);
                    setCell(row, C_VENDOR,   vendorCodeForExcel, borderStyle);
                    setCell(row, C_NAME,     empName,    borderStyle);
                    setCell(row, C_ROLE,     roleName,   borderStyle);
                    setCell(row, C_PROJECT,  projId,     borderStyle);  // E: projectId
                    setCell(row, C_KEY,      projKey,    borderStyle);
                    setCell(row, C_TCBUSER,  tcbUser,    borderStyle);
                    setCell(row, C_LOCATION, location,   borderStyle);
                    setCell(row, C_ONBOARD,  onboardStr, borderStyle);

                    // Daily hours (regular)
                    for (int d = 1; d <= daysInMonth; d++) {
                        LocalDate date = ym.atDay(d);
                        int dow = date.getDayOfWeek().getValue() % 7;
                        boolean isSun = (dow == 0), isSat = (dow == 6);
                        XSSFCellStyle dStyle = isSun ? sunStyle : (isSat ? satStyle : borderStyle);
                        XSSFCell cell = (XSSFCell) row.createCell(C_DAY + d - 1);
                        BigDecimal h = projDayH.get(date);
                        if (isSun || h == null || h.compareTo(BigDecimal.ZERO) == 0) {
                            cell.setCellStyle(dStyle);
                        } else {
                            cell.setCellValue(h.doubleValue());
                            cell.setCellStyle(dStyle);
                        }
                    }

                    // Summary formulas
                    String sumRange = colLetter(C_DAY) + rowNum + ":" + colLetter(C_DAY + daysInMonth - 1) + rowNum;
                    String totalHRef = colLetter(C_TOTAL_H) + rowNum;
                    String rateRef   = colLetter(C_RATE)    + rowNum;

                    XSSFCell totalHCell = (XSSFCell) row.createCell(C_TOTAL_H);
                    totalHCell.setCellFormula("SUM(" + sumRange + ")");
                    totalHCell.setCellStyle(numStyle);

                    XSSFCell mandayCell = (XSSFCell) row.createCell(C_MANDAY);
                    mandayCell.setCellFormula(totalHRef + "/8");
                    mandayCell.setCellStyle(numStyle);

                    setCell(row, C_RATE, hourlyRate.doubleValue(), amtStyle);

                    XSSFCell totalAmtCell = (XSSFCell) row.createCell(C_TOTAL_AMT);
                    totalAmtCell.setCellFormula(totalHRef + "*" + rateRef);
                    totalAmtCell.setCellStyle(amtStyle);

                    setCell(row, C_OT_STATUS, "100%", borderStyle);

                    // Custom field values from TimesheetMonthly.additions
                    TimesheetMonthly monthly = emp != null && emp.getUserId() != null
                            ? monthlyMetaByUserId.get(emp.getUserId()) : null;
                    Map<String, Object> additions = parseAdditions(monthly != null ? monthly.getAdditions() : null);
                    for (int ci = 0; ci < numCustomFields; ci++) {
                        CompanyTimesheetField cf = customFields.get(ci);
                        Object val = additions.get(cf.getFieldKey());
                        String strVal = val != null ? val.toString() : "";
                        setCell(row, C_CUSTOM_START + ci, strVal, borderStyle);
                    }

                    // Note = Mô tả timesheet của nhân sự
                    String desc = monthly != null && monthly.getDescription() != null ? monthly.getDescription() : "";
                    setCell(row, C_NOTE, desc, wrapStyle);

                    // Approve TS = raw timesheet hours for this project key
                    double projApprove = projDayH.values().stream()
                            .mapToDouble(BigDecimal::doubleValue).sum();
                    setCell(row, C_APPROVE_TS, projApprove, numStyle);

                    row.createCell(C_INOUT).setCellStyle(borderStyle);

                    // Giải trình only on first row per employee
                    setCell(row, C_GIAI_TRINH,
                            firstProjectRow && item.getNote() != null ? item.getNote() : "",
                            wrapStyle);

                    // Collect OT data for this project key
                    Map<LocalDate, BigDecimal> projOtH = otByProject.get(projKey);
                    if (projOtH != null && !projOtH.isEmpty()) {
                        otRows.add(new OtRowData(emp, role, assign, projKey,
                                projectIdByKey.getOrDefault(projKey, ""),
                                projectNameByKey.getOrDefault(projKey, ""), projOtH, item));
                    }

                    firstProjectRow = false;
                }

                // Collect OT for project keys that appear only in OT map (rare edge case)
                for (Map.Entry<String, Map<LocalDate, BigDecimal>> oe : otByProject.entrySet()) {
                    if (!regularByProject.containsKey(oe.getKey())) {
                        otRows.add(new OtRowData(emp, role, assign, oe.getKey(),
                                projectIdByKey.getOrDefault(oe.getKey(), ""),
                                projectNameByKey.getOrDefault(oe.getKey(), ""), oe.getValue(), item));
                    }
                }
            }

            // ── OT rows (appended after all regular rows) ──────────────────────
            for (OtRowData ot : otRows) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(15);

                BigDecimal hpd = ot.item().getHoursPerDay() != null && ot.item().getHoursPerDay().compareTo(BigDecimal.ZERO) > 0
                        ? ot.item().getHoursPerDay() : BigDecimal.valueOf(8);
                BigDecimal baseRate = ot.item().getUnitSellPrice() != null
                        ? ot.item().getUnitSellPrice().divide(hpd, 0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                // OT rate = 1.5 × hourly rate
                BigDecimal otRate = baseRate.multiply(new BigDecimal("1.5")).setScale(0, RoundingMode.HALF_UP);

                String otEmpName    = ot.emp()    != null ? ot.emp().getFullName()    : "";
                String otRoleName   = ot.role()   != null ? ot.role().getName()       : "";
                String otProjName   = ot.projectName();
                String otTcbUser    = ot.assign() != null && ot.assign().getClientUsername() != null ? ot.assign().getClientUsername() : "";
                String otLocation   = ot.assign() != null && ot.assign().getWorkLocation()   != null ? ot.assign().getWorkLocation()   : "";
                String otOnboard    = ot.emp() != null && ot.emp().getStartDate() != null
                        ? ot.emp().getStartDate().format(ddMMyyyy) : "";

                setCell(row, C_NO,       stt++,            numStyle);
                setCell(row, C_VENDOR,   vendorCodeForExcel, borderStyle);
                setCell(row, C_NAME,     otEmpName,        borderStyle);
                setCell(row, C_ROLE,     otRoleName,       borderStyle);
                setCell(row, C_PROJECT,  ot.projectId(),   borderStyle); // E: projectId
                setCell(row, C_KEY,      ot.projectKey(),  borderStyle);
                setCell(row, C_TCBUSER,  otTcbUser,        borderStyle);
                setCell(row, C_LOCATION, otLocation,       borderStyle);
                setCell(row, C_ONBOARD,  otOnboard,        borderStyle);

                // OT daily hours
                for (int d = 1; d <= daysInMonth; d++) {
                    LocalDate date = ym.atDay(d);
                    int dow = date.getDayOfWeek().getValue() % 7;
                    boolean isSun = (dow == 0), isSat = (dow == 6);
                    XSSFCellStyle dStyle = isSun ? sunStyle : (isSat ? satStyle : borderStyle);
                    XSSFCell cell = (XSSFCell) row.createCell(C_DAY + d - 1);
                    BigDecimal h = ot.dayOtHours().get(date);
                    if (isSun || h == null || h.compareTo(BigDecimal.ZERO) == 0) {
                        cell.setCellStyle(dStyle);
                    } else {
                        cell.setCellValue(h.doubleValue());
                        cell.setCellStyle(dStyle);
                    }
                }

                String sumRange  = colLetter(C_DAY) + rowNum + ":" + colLetter(C_DAY + daysInMonth - 1) + rowNum;
                String totalHRef = colLetter(C_TOTAL_H) + rowNum;
                String rateRef   = colLetter(C_RATE)    + rowNum;

                XSSFCell totalHCell = (XSSFCell) row.createCell(C_TOTAL_H);
                totalHCell.setCellFormula("SUM(" + sumRange + ")");
                totalHCell.setCellStyle(numStyle);

                XSSFCell mandayCell = (XSSFCell) row.createCell(C_MANDAY);
                mandayCell.setCellFormula(totalHRef + "/8");
                mandayCell.setCellStyle(numStyle);

                setCell(row, C_RATE, otRate.doubleValue(), amtStyle);

                XSSFCell totalAmtCell = (XSSFCell) row.createCell(C_TOTAL_AMT);
                totalAmtCell.setCellFormula(totalHRef + "*" + rateRef);
                totalAmtCell.setCellStyle(amtStyle);

                // OT Status = 150%
                setCell(row, C_OT_STATUS, "150%", borderStyle);

                // Custom field values (same employee monthly data)
                TimesheetMonthly otMonthly = ot.emp() != null && ot.emp().getUserId() != null
                        ? monthlyMetaByUserId.get(ot.emp().getUserId()) : null;
                Map<String, Object> otAdditions = parseAdditions(otMonthly != null ? otMonthly.getAdditions() : null);
                for (int ci = 0; ci < numCustomFields; ci++) {
                    CompanyTimesheetField cf = customFields.get(ci);
                    Object val = otAdditions.get(cf.getFieldKey());
                    setCell(row, C_CUSTOM_START + ci, val != null ? val.toString() : "", borderStyle);
                }

                // Note = Mô tả timesheet
                String otDesc = otMonthly != null && otMonthly.getDescription() != null ? otMonthly.getDescription() : "";
                setCell(row, C_NOTE, otDesc, wrapStyle);

                double otApprove = ot.dayOtHours().values().stream()
                        .mapToDouble(BigDecimal::doubleValue).sum();
                setCell(row, C_APPROVE_TS, otApprove, numStyle);

                row.createCell(C_INOUT).setCellStyle(borderStyle);
                row.createCell(C_GIAI_TRINH).setCellStyle(wrapStyle);
            }

            // ── Total row — yellow background, red bold text ───────────────────
            XSSFCellStyle grandLabelStyle = grandTotalLabelStyle(wb);
            XSSFCellStyle grandAmtStyle   = grandTotalAmountStyle(wb);

            int totalRowIdx = rowNum + 1;
            Row tRow = sheet.createRow(totalRowIdx);
            tRow.setHeightInPoints(18);

            // Các cột trước Total BCC hour: không border (trong suốt)
            XSSFCellStyle noBorderStyle = wb.createCellStyle();
            for (int mc = C_NO; mc < C_TOTAL_H; mc++) {
                tRow.createCell(mc).setCellStyle(noBorderStyle);
            }

            // Merged label: Total BCC hour → Hourly Rate (C_RATE), yellow + red bold
            XSSFCell tLabelCell = (XSSFCell) tRow.createCell(C_TOTAL_H);
            tLabelCell.setCellValue("Tổng cộng thanh toán");
            tLabelCell.setCellStyle(grandLabelStyle);
            sheet.addMergedRegion(new CellRangeAddress(totalRowIdx, totalRowIdx, C_TOTAL_H, C_RATE));
            // Fill merged cells with same style so borders render correctly
            for (int mc = C_TOTAL_H + 1; mc <= C_RATE; mc++) {
                tRow.createCell(mc).setCellStyle(grandLabelStyle);
            }

            // Total amount: on C_TOTAL_AMT column, yellow + red bold
            String totalAmtFormula = "SUM(" + colLetter(C_TOTAL_AMT) + dataRowStart + ":" + colLetter(C_TOTAL_AMT) + rowNum + ")";
            XSSFCell tTotalAmt = (XSSFCell) tRow.createCell(C_TOTAL_AMT);
            tTotalAmt.setCellFormula(totalAmtFormula);
            tTotalAmt.setCellStyle(grandAmtStyle);


            // Freeze panes: freeze first 9 cols (A-I) + header rows (data starts at row 7)
            sheet.createFreezePane(C_DAY, 7); // C_DAY = 9

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Export failed", HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_ERROR");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAdditions(String additions) {
        if (additions == null || additions.isBlank()) return Collections.emptyMap();
        try {
            return OBJECT_MAPPER.readValue(additions, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    // ── Excel style helpers ───────────────────────────────────────────────────

    private void setMergedCenteredBold(XSSFSheet sheet, int rowIdx, int c1, int c2, String text,
                                       XSSFWorkbook wb, int fontSize) {
        Row row = sheet.createRow(rowIdx);
        XSSFCell cell = (XSSFCell) row.createCell(c1);
        cell.setCellValue(text);
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);
        cell.setCellStyle(style);
        if (c1 < c2) sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, c1, c2));
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        XSSFCell c = (XSSFCell) row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    private void setCell(Row row, int col, double value, CellStyle style) {
        XSSFCell c = (XSSFCell) row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private void setCell(Row row, int col, int value, CellStyle style) {
        XSSFCell c = (XSSFCell) row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    /** Convert 0-based column index to Excel letter (A, B, ..., Z, AA, ...) */
    private String colLetter(int col) {
        StringBuilder sb = new StringBuilder();
        col++; // convert to 1-based
        while (col > 0) {
            sb.insert(0, (char) ('A' + (col - 1) % 26));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private static final String FONT_NAME = "Times New Roman";
    private static final short FONT_SIZE = 9;

    private XSSFFont baseFont(XSSFWorkbook wb) {
        XSSFFont f = wb.createFont();
        f.setFontName(FONT_NAME);
        f.setFontHeightInPoints(FONT_SIZE);
        return f;
    }

    private XSSFCellStyle centerBoldStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        XSSFFont f = baseFont(wb); f.setBold(true);
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle colHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xDB,(byte)0xDB,(byte)0xDB}, null)); // light gray (#DBDBDB)
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = baseFont(wb); f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle dayHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xBD,(byte)0xD7,(byte)0xEE}, null)); // light blue
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = baseFont(wb); f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle sunColumnStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xD9,(byte)0xD9,(byte)0xD9}, null)); // light gray
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle satColumnStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xCC}, null)); // light yellow
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle numberStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle amountStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFDataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle totalRowStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb); f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFDataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        setBorderThin(s);
        return s;
    }

    /** Ô nhãn "Tổng cộng thanh toán": nền vàng, chữ đỏ đậm, căn giữa. */
    private XSSFCellStyle grandTotalLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        f.setBold(true);
        f.setColor(new XSSFColor(new byte[]{(byte)192, 0, 0}, null)); // dark red
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)255, 0}, null)); // yellow
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    /** Ô số tổng Total(VND): nền vàng, chữ đỏ đậm, số có dấu phẩy, căn phải. */
    private XSSFCellStyle grandTotalAmountStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        f.setBold(true);
        f.setColor(new XSSFColor(new byte[]{(byte)192, 0, 0}, null)); // dark red
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)255, 0}, null)); // yellow
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFDataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle wrapTextStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        setBorderThin(s);
        return s;
    }

    private XSSFCellStyle borderCellStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = baseFont(wb);
        s.setFont(f);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(s);
        return s;
    }

    private void setBorderThin(XSSFCellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private Statement findStatement(Long companyId, String type, Long recipientId, String yearMonth) {
        return statementRepository.findByCompanyIdAndStatementTypeAndRecipientIdAndStatementMonth(
                        companyId, type, recipientId, yearMonth)
                .orElseThrow(() -> new BusinessException("Statement not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private StatementDto toDto(Statement s, boolean withLineItems) {
        List<StatementLineItemDto> items = List.of();
        if (withLineItems && !s.getLineItems().isEmpty()) {
            Set<Long> empIds = s.getLineItems().stream().map(StatementLineItem::getEmployeeId).collect(Collectors.toSet());
            Map<Long, Employee> empMap = employeeRepository.findAllById(empIds).stream()
                    .collect(Collectors.toMap(Employee::getId, Function.identity()));

            // Fetch periods context cho từng line item (BillingRecord thuộc recipient + month)
            String month = s.getStatementMonth();
            String stmtType = s.getStatementType();
            Long recipientId = s.getRecipientId();
            Long companyId = s.getCompanyId();

            // Bulk fetch: mỗi NV → list billing records trong tháng
            Map<Long, List<BillingRecord>> billingByEmp = new HashMap<>();
            // Bulk fetch timesheet days theo NV để tính orphan flag
            Map<Long, Set<LocalDate>> timesheetDaysByEmp = new HashMap<>();
            YearMonth ym = YearMonth.parse(month);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            for (Long eid : empIds) {
                List<BillingRecord> recs = billingRecordRepository
                        .findByCompanyAndEmployeeAndMonth(companyId, eid, month);
                billingByEmp.put(eid, recs);

                Employee em = empMap.get(eid);
                if (em != null && em.getUserId() != null) {
                    Set<LocalDate> days = timesheetRepository
                            .findByUserAndMonth(em.getUserId(), companyId, monthStart, monthEnd)
                            .stream()
                            .filter(t -> t.getHours() != null && t.getHours().compareTo(BigDecimal.ZERO) > 0)
                            .map(Timesheet::getWorkDate)
                            .collect(Collectors.toSet());
                    timesheetDaysByEmp.put(eid, days);
                } else {
                    timesheetDaysByEmp.put(eid, Set.of());
                }
            }

            items = s.getLineItems().stream().map(li -> {
                List<BillingRecord> recipientPeriods = billingByEmp.getOrDefault(li.getEmployeeId(), List.of())
                        .stream()
                        .filter(b -> matchesRecipient(b, stmtType, recipientId))
                        .toList();
                List<StatementPeriodDto> periodDtos = recipientPeriods.stream()
                        .map(b -> new StatementPeriodDto(b.getPeriodStart(), b.getPeriodEnd(),
                                b.getCustomerId(), b.getVendorId()))
                        .toList();

                // Orphan = có timesheet vào ngày KHÔNG nằm trong period nào của recipient này
                Set<LocalDate> tsDays = timesheetDaysByEmp.getOrDefault(li.getEmployeeId(), Set.of());
                boolean orphan = tsDays.stream().anyMatch(d -> !isInAnyPeriod(d, recipientPeriods));

                return toLineItemDto(li, empMap.get(li.getEmployeeId()), periodDtos, orphan);
            }).toList();
        }
        String recipientName = resolveRecipientName(s);

        return new StatementDto(s.getId(), s.getStatementType(), s.getRecipientId(),
                recipientName, s.getStatementMonth(), s.getTotalEmployees(),
                s.getTotalDaysWorked(), s.getTotalAmount(), s.getCurrency(),
                s.getStatus(), s.getSentAt(), s.getSentBy(),
                s.getConfirmedAt(), s.getConfirmedBy(), s.getExportedAt(),
                s.getStaleReason(), s.getNote(), items);
    }

    private String resolveRecipientName(Statement s) {
        if (s.getRecipientId() == null) return null;
        try {
            if ("CUSTOMER".equals(s.getStatementType())) {
                return customerRepository.findById(s.getRecipientId()).map(c -> c.getName()).orElse(null);
            } else if ("VENDOR".equals(s.getStatementType())) {
                return vendorRepository.findById(s.getRecipientId()).map(v -> v.getName()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve recipient name for statement {}: {}", s.getId(), e.getMessage());
        }
        return null;
    }

    private StatementLineItemDto toLineItemDto(StatementLineItem li, Employee emp) {
        return toLineItemDto(li, emp, List.of(), false);
    }

    private StatementLineItemDto toLineItemDto(StatementLineItem li, Employee emp,
                                                List<StatementPeriodDto> periods, boolean hasOrphanTimesheet) {
        // Fallback lookup nếu emp null (vd: updateLineItem chỉ truyền null) để FE luôn có userId
        Employee resolved = emp != null
                ? emp
                : employeeRepository.findById(li.getEmployeeId()).orElse(null);
        return new StatementLineItemDto(li.getId(), li.getEmployeeId(),
                resolved != null ? resolved.getUserId() : null,
                resolved != null ? resolved.getEmployeeCode() : null,
                resolved != null ? resolved.getFullName() : null,
                null,
                li.getOriginalDays(), li.getAdjustedDays(),
                li.getOriginalHours(), li.getAdjustedHours(), li.getHoursPerDay(),
                li.getUnitSellPrice(), li.getOriginalAmount(), li.getAdjustedAmount(),
                li.getNote(),
                periods != null ? periods : List.of(),
                hasOrphanTimesheet);
    }
}
