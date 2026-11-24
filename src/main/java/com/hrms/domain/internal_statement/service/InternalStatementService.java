package com.hrms.domain.internal_statement.service;

import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.calendar.service.WorkCalendarService;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.internal_statement.dto.*;
import com.hrms.domain.internal_statement.entity.InternalStatementEntry;
import com.hrms.domain.internal_statement.entity.InternalStatementSummary;
import com.hrms.domain.internal_statement.repository.InternalStatementEntryRepository;
import com.hrms.domain.internal_statement.repository.InternalStatementSummaryRepository;
import com.hrms.domain.leave.entity.LeaveRequest;
import com.hrms.domain.leave.entity.LeaveType;
import com.hrms.domain.leave.repository.LeaveRequestRepository;
import com.hrms.domain.leave.repository.LeaveTypeRepository;
import com.hrms.domain.timesheet.entity.Timesheet;
import com.hrms.domain.timesheet.entity.TimesheetPeriodLock;
import com.hrms.domain.timesheet.repository.TimesheetRepository;
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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalStatementService {

    private static final BigDecimal STD_HOURS = BigDecimal.valueOf(8);
    private static final BigDecimal HALF_DAY_HOURS = BigDecimal.valueOf(4);

    private final InternalStatementEntryRepository entryRepo;
    private final InternalStatementSummaryRepository summaryRepo;
    private final EmployeeRepository employeeRepo;
    private final TimesheetRepository timesheetRepo;
    private final LeaveRequestRepository leaveRequestRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final TimesheetPeriodLockRepository timesheetPeriodLockRepository;
    private final WorkCalendarService workCalendarService;
    private final PayrollRecordRepository payrollRecordRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. Generate / Refresh toàn bộ nhân sự trong tháng
    // ─────────────────────────────────────────────────────────────
    // Bug 4: Lock đơn giản để tránh 2 admin generate cùng lúc
    private final Set<String> generatingLocks = Collections.synchronizedSet(new HashSet<>());

    /**
     * Biên bản nội bộ là đầu vào của bảng lương, nên chỉ được tổng hợp khi
     * Timesheet đã bị khóa và được Kế toán xác nhận chốt số liệu.
     */
    private void assertTimesheetAccountantConfirmed(Long companyId, String yearMonth) {
        TimesheetPeriodLock lock = timesheetPeriodLockRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseThrow(() -> new BusinessException(
                        "Timesheet tháng " + yearMonth
                                + " chưa được khóa và Kế toán xác nhận. Vui lòng hoàn tất bước xác nhận timesheet trước khi tạo biên bản nội bộ.",
                        HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_ACCOUNTANT_CONFIRMED"));
        if (!lock.isLocked() || !lock.isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth
                            + " chưa được Kế toán xác nhận. Vui lòng xác nhận timesheet trước khi tạo biên bản nội bộ.",
                    HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_ACCOUNTANT_CONFIRMED");
        }
    }

    @Transactional
    public void generateForMonth(Long companyId, String yearMonth) {
        // Bug 8: Validate yearMonth
        YearMonth ym;
        try {
            ym = YearMonth.parse(yearMonth);
        } catch (Exception e) {
            throw new BusinessException("Tháng không hợp lệ: " + yearMonth + ". Định dạng: YYYY-MM",
                    HttpStatus.BAD_REQUEST, "INVALID_MONTH");
        }

        assertTimesheetAccountantConfirmed(companyId, yearMonth);

        // Bug 4: Chống race condition — chỉ 1 request generate cùng lúc cho 1 company+month
        String lockKey = companyId + ":" + yearMonth;
        if (!generatingLocks.add(lockKey)) {
            throw new BusinessException("Đang có người khác tổng hợp tháng " + yearMonth + ". Vui lòng thử lại.",
                    HttpStatus.CONFLICT, "GENERATE_IN_PROGRESS");
        }
        try {
            doGenerateForMonth(companyId, yearMonth, ym);
        } finally {
            generatingLocks.remove(lockKey);
        }
    }

    private void doGenerateForMonth(Long companyId, String yearMonth, YearMonth ym) {
        // Bug 2: Chặn generate khi statement đã CONFIRMED/CLOSED
        List<InternalStatementSummary> existingSummaries = summaryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth);
        boolean hasFinalized = existingSummaries.stream()
                .anyMatch(s -> !"DRAFT".equals(s.getStatus()));
        if (hasFinalized) {
            throw new BusinessException(
                    "Tháng " + yearMonth + " đã CLOSED. Không thể tổng hợp lại.",
                    HttpStatus.CONFLICT, "STATEMENT_FINALIZED");
        }

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Employee> allEmployees = employeeRepo.findByCompanyIdAndDeletedAtIsNull(companyId);
        Map<Long, LeaveType> leaveTypeMap = leaveTypeRepo.findByCompanyId(companyId)
                .stream().collect(Collectors.toMap(LeaveType::getId, lt -> lt));

        // Bug 1: Chỉ lấy APPROVED leaves (không lấy PENDING)
        // Payroll phải bao phủ cả nhân sự nội bộ không log giờ: 0h là nghỉ không lương
        // nếu không có phép hưởng lương hoặc điều chỉnh được xác nhận.
        List<Employee> relevantEmployees = allEmployees.stream()
                .filter(emp -> emp.getSourceType() == EmployeeSourceType.INTERNAL)
                .filter(emp -> !emp.getStartDate().isAfter(monthEnd))
                .filter(emp -> emp.getEndDate() == null || !emp.getEndDate().isBefore(monthStart))
                .toList();

        // Collect IDs of employees that SHOULD have data
        Set<Long> relevantEmpIds = relevantEmployees.stream()
                .map(Employee::getId).collect(Collectors.toSet());

        for (Employee emp : relevantEmployees) {
            generateForEmployee(companyId, emp, yearMonth, leaveTypeMap);
        }

        // Bug 3: Xóa summary + entries mồ côi (NV không còn relevant)
        for (InternalStatementSummary orphan : existingSummaries) {
            if (!relevantEmpIds.contains(orphan.getEmployeeId())) {
                entryRepo.deleteByCompanyIdAndEmployeeIdAndYearMonth(
                        companyId, orphan.getEmployeeId(), yearMonth);
                summaryRepo.delete(orphan);
                log.info("Removed orphaned internal statement for employee={}, month={}",
                        orphan.getEmployeeId(), yearMonth);
            }
        }

        log.info("Generated internal statements for {} employees (of {} total), company={}, month={}",
                relevantEmployees.size(), allEmployees.size(), companyId, yearMonth);
    }

    @Transactional
    public void generateForEmployee(Long companyId, Long employeeId, String yearMonth) {
        assertTimesheetAccountantConfirmed(companyId, yearMonth);
        if (isConfirmed(companyId, employeeId, yearMonth)) {
            throw new BusinessException("Biên bản đã xác nhận; hãy mở lại trước khi tổng hợp", HttpStatus.CONFLICT, "STATEMENT_CONFIRMED");
        }
        Employee emp = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new BusinessException("Employee not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Map<Long, LeaveType> leaveTypeMap = leaveTypeRepo.findByCompanyId(companyId)
                .stream().collect(Collectors.toMap(LeaveType::getId, lt -> lt));
        generateForEmployee(companyId, emp, yearMonth, leaveTypeMap);
    }

    private void generateForEmployee(Long companyId, Employee emp, String yearMonth,
                                     Map<Long, LeaveType> leaveTypeMap) {
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // Lấy timesheet (nếu employee có userId)
        Map<LocalDate, BigDecimal> rawHoursMap = new HashMap<>();
        Map<LocalDate, String> timesheetNoteMap = new HashMap<>();
        Map<LocalDate, BigDecimal> rawOtHoursMap = new HashMap<>();
        if (emp.getUserId() != null) {
            List<Timesheet> entries = timesheetRepo.findByUserAndMonth(emp.getUserId(), companyId, monthStart, monthEnd);
            for (Timesheet t : entries) {
                rawHoursMap.merge(t.getWorkDate(), t.getHours(), BigDecimal::add);
                rawOtHoursMap.merge(t.getWorkDate(),
                        t.getOtHours() != null ? t.getOtHours() : BigDecimal.ZERO, BigDecimal::add);
                if (t.getNote() != null && !t.getNote().isBlank()) {
                    timesheetNoteMap.put(t.getWorkDate(), t.getNote());
                }
            }
        }

        // Bug 1: Chỉ lấy APPROVED (không lấy PENDING)
        List<LeaveRequest> leaves = leaveRequestRepo.findOnlyApprovedByEmployeeAndDateRange(
                companyId, emp.getId(), monthStart, monthEnd);

        // Map từng ngày → leave info (ưu tiên entry đầu tiên nếu có nhiều)
        Map<LocalDate, LeaveRequest> dateLeaveMap = new HashMap<>();
        for (LeaveRequest lr : leaves) {
            LocalDate cur = lr.getStartDate().isBefore(monthStart) ? monthStart : lr.getStartDate();
            LocalDate end = lr.getEndDate().isAfter(monthEnd) ? monthEnd : lr.getEndDate();
            while (!cur.isAfter(end)) {
                dateLeaveMap.putIfAbsent(cur, lr);
                cur = cur.plusDays(1);
            }
        }

        // Tập hợp tất cả ngày cần tạo entry
        Set<LocalDate> activeDates = new HashSet<>();
        activeDates.addAll(rawHoursMap.keySet());
        activeDates.addAll(dateLeaveMap.keySet());
        activeDates.addAll(rawOtHoursMap.keySet());
        LocalDate activeStart = emp.getStartDate().isAfter(monthStart) ? emp.getStartDate() : monthStart;
        LocalDate activeEnd = emp.getEndDate() != null && emp.getEndDate().isBefore(monthEnd) ? emp.getEndDate() : monthEnd;
        for (LocalDate day = activeStart; !day.isAfter(activeEnd); day = day.plusDays(1)) activeDates.add(day);

        // Giữ lại những entry đã HR override thủ công (không reset adjustedHours)
        Map<LocalDate, InternalStatementEntry> existingMap = entryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, emp.getId(), yearMonth)
                .stream().collect(Collectors.toMap(InternalStatementEntry::getWorkDate, e -> e));

        List<InternalStatementEntry> toSave = new ArrayList<>();
        List<InternalStatementEntry> toDelete = new ArrayList<>();

        // 1. Duyệt activeDates: tạo mới hoặc cập nhật entry
        for (LocalDate date : activeDates) {
            BigDecimal raw = rawHoursMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal rawOt = rawOtHoursMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal expected = workCalendarService.isWorkday(companyId, date) ? STD_HOURS : BigDecimal.ZERO;
            LeaveRequest lr = dateLeaveMap.get(date);
            LeaveType lt = lr != null ? leaveTypeMap.get(lr.getLeaveTypeId()) : null;
            boolean isPaid = lt != null && lt.isPaid();
            boolean isHalf = lr != null && lr.isHalfDay();
            boolean isLeave = lr != null;

            // Tính adjusted_hours mặc định
            // Bug 5: cap adjusted tối đa STD_HOURS (8h) cho nửa ngày phép
            BigDecimal adjusted;
            if (isLeave && isPaid) {
                if (isHalf) {
                    // Nửa ngày phép có lương: raw + 4h, tối đa 8h
                    adjusted = raw.add(HALF_DAY_HOURS).min(STD_HOURS);
                } else {
                    // Cả ngày phép có lương: đảm bảo tối thiểu 8h
                    adjusted = raw.max(STD_HOURS);
                }
            } else {
                adjusted = raw;
            }

            InternalStatementEntry entry = existingMap.get(date);
            if (entry == null) {
                // Entry mới: set adjustedHours tự động
                entry = InternalStatementEntry.builder()
                        .companyId(companyId)
                        .employeeId(emp.getId())
                        .yearMonth(yearMonth)
                        .workDate(date)
                        .expectedHours(expected)
                        .rawHours(raw)
                        .rawOtHours(rawOt)
                        .adjustedHours(adjusted)
                        .adjustedOtHours(rawOt)
                        .otMultiplier(BigDecimal.valueOf(1.5))
                        .leaveDay(isLeave && !isHalf)
                        .halfLeave(isHalf)
                        .paidLeave(isPaid)
                        .leaveTypeName(lt != null ? lt.getName() : null)
                        .build();
            } else {
                // Entry đã tồn tại: cập nhật raw, leave info VÀ tính lại adjustedHours
                entry.setRawHours(raw);
                entry.setRawOtHours(rawOt);
                entry.setExpectedHours(expected);
                // DRAFT có thể refresh từ nguồn; CONFIRMED là snapshot bất biến.
                if (!isConfirmed(companyId, emp.getId(), yearMonth)) {
                    entry.setAdjustedHours(adjusted);
                    entry.setAdjustedOtHours(rawOt);
                }
                entry.setLeaveDay(isLeave && !isHalf);
                entry.setHalfLeave(isHalf);
                entry.setPaidLeave(isPaid);
                entry.setLeaveTypeName(lt != null ? lt.getName() : null);
            }
            toSave.add(entry);
        }

        // 2. Xóa entry cũ mà ngày đó không còn trong activeDates
        //    (VD: phép đã bị hủy + không có timesheet → entry thừa)
        for (Map.Entry<LocalDate, InternalStatementEntry> old : existingMap.entrySet()) {
            if (!activeDates.contains(old.getKey())) {
                toDelete.add(old.getValue());
            }
        }

        entryRepo.saveAll(toSave);
        if (!toDelete.isEmpty()) {
            entryRepo.deleteAll(toDelete);
        }
        recalculateSummary(companyId, emp.getId(), yearMonth);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. HR chỉnh sửa 1 ngày
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public InternalCalendarDayDto updateDay(Long companyId, Long employeeId, String yearMonthStr,
                                             LocalDate workDate, UpdateInternalDayRequest req, Long userId) {
        // Kiểm tra biên bản đã đóng
        summaryRepo.findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonthStr)
                .ifPresent(s -> {
                    if (!"DRAFT".equals(s.getStatus())) {
                        throw new BusinessException(
                                "Biên bản nội bộ tháng " + yearMonthStr + " đã đóng. Không thể chỉnh sửa.",
                                HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
                    }
                });

        // Tìm hoặc tạo entry
        InternalStatementEntry entry = entryRepo
                .findByCompanyIdAndEmployeeIdAndWorkDate(companyId, employeeId, workDate)
                .orElse(InternalStatementEntry.builder()
                        .companyId(companyId)
                        .employeeId(employeeId)
                        .yearMonth(yearMonthStr)
                        .workDate(workDate)
                        .rawHours(BigDecimal.ZERO)
                        .rawOtHours(BigDecimal.ZERO)
                        .adjustedHours(BigDecimal.ZERO)
                        .adjustedOtHours(BigDecimal.ZERO)
                        .otMultiplier(BigDecimal.valueOf(1.5))
                        .build());

        BigDecimal adjustedHours = req.adjustedHours() != null ? req.adjustedHours() : BigDecimal.ZERO;
        BigDecimal adjustedOtHours = req.adjustedOtHours() != null ? req.adjustedOtHours() : BigDecimal.ZERO;
        BigDecimal multiplier = req.otMultiplier() != null ? req.otMultiplier() : BigDecimal.valueOf(1.5);
        if (adjustedHours.signum() < 0 || adjustedHours.compareTo(BigDecimal.valueOf(24)) > 0
                || adjustedOtHours.signum() < 0 || adjustedOtHours.compareTo(BigDecimal.valueOf(24)) > 0
                || multiplier.compareTo(BigDecimal.ZERO) <= 0 || multiplier.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BusinessException("Giờ công/OT hoặc hệ số OT không hợp lệ", HttpStatus.BAD_REQUEST, "INVALID_HOURS");
        }
        if (multiplier.compareTo(BigDecimal.valueOf(1.5)) != 0
                && (req.otAdjustmentReason() == null || req.otAdjustmentReason().isBlank())) {
            throw new BusinessException("Cần nêu lý do khi đổi hệ số OT", HttpStatus.BAD_REQUEST, "OT_REASON_REQUIRED");
        }
        entry.setAdjustedHours(adjustedHours);
        entry.setAdjustedOtHours(adjustedOtHours);
        entry.setOtMultiplier(multiplier);
        entry.setOtAdjustmentReason(req.otAdjustmentReason());
        entry.setNote(req.note());
        entry.setUpdatedBy(userId);
        entry = entryRepo.save(entry);
        recalculateSummary(companyId, employeeId, yearMonthStr);

        // Lấy timesheet note để trả về
        String tsNote = getTimesheetNote(companyId, employeeId, workDate);
        return toCalendarDay(entry, tsNote);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Lấy tổng quan (list all employees)
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<InternalOverviewItemDto> getOverview(Long companyId, String yearMonth) {
        // Chỉ lấy NV có summary VÀ có giờ > 0 hoặc có phép trong tháng
        Map<Long, InternalStatementSummary> summaryMap = summaryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .stream().collect(Collectors.toMap(InternalStatementSummary::getEmployeeId, s -> s));

        if (summaryMap.isEmpty()) return List.of();

        Map<Long, Employee> empMap = employeeRepo.findAllById(summaryMap.keySet())
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));

        return summaryMap.entrySet().stream()
                .map(entry -> {
                    Employee emp = empMap.get(entry.getKey());
                    if (emp == null) return null;
                    InternalStatementSummary s = entry.getValue();
                    return new InternalOverviewItemDto(emp.getId(), emp.getPublicId(), emp.getEmployeeCode(), emp.getFullName(),
                            s.getTotalRawHours(), s.getTotalAdjustedHours(), s.getTotalAdjustedOtHours(), s.getTotalAdjustedDays(),
                            s.getTotalLeaveDays(), s.getTotalHalfLeaveDays(), s.getStatus());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Lấy calendar per nhân sự
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public InternalEmployeeCalendarDto getEmployeeCalendar(Long companyId, Long employeeId, String yearMonth) {
        Employee emp = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new BusinessException("Employee not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // Map entry theo ngày
        Map<LocalDate, InternalStatementEntry> entryMap = entryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .stream().collect(Collectors.toMap(InternalStatementEntry::getWorkDate, e -> e));

        // Timesheet notes
        Map<LocalDate, String> tsNotes = new HashMap<>();
        if (emp.getUserId() != null) {
            timesheetRepo.findByUserAndMonth(emp.getUserId(), companyId, monthStart, monthEnd)
                    .stream().filter(t -> t.getNote() != null && !t.getNote().isBlank())
                    .forEach(t -> tsNotes.put(t.getWorkDate(), t.getNote()));
        }

        // Tạo full calendar (tất cả ngày trong tháng)
        List<InternalCalendarDayDto> days = new ArrayList<>();
        LocalDate cur = monthStart;
        while (!cur.isAfter(monthEnd)) {
            InternalStatementEntry entry = entryMap.get(cur);
            String tsNote = tsNotes.get(cur);
            if (entry != null) {
                days.add(toCalendarDay(entry, tsNote));
            } else {
                // Ngày không có data: hiển thị 0H
                DayOfWeek dow = cur.getDayOfWeek();
                boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                days.add(new InternalCalendarDayDto(cur, dow.name().substring(0, 3),
                        isWeekend, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.valueOf(1.5), null,
                        false, false, false, null, tsNote, null, null));
            }
            cur = cur.plusDays(1);
        }

        InternalStatementSummary summary = summaryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .orElse(null);

        BigDecimal totalExpected = summary != null ? summary.getTotalExpectedHours() : BigDecimal.ZERO;
        BigDecimal totalRaw = summary != null ? summary.getTotalRawHours() : BigDecimal.ZERO;
        BigDecimal totalAdj = summary != null ? summary.getTotalAdjustedHours() : BigDecimal.ZERO;
        BigDecimal totalRawOt = summary != null ? summary.getTotalRawOtHours() : BigDecimal.ZERO;
        BigDecimal totalAdjustedOt = summary != null ? summary.getTotalAdjustedOtHours() : BigDecimal.ZERO;
        BigDecimal totalDays = summary != null ? summary.getTotalAdjustedDays() : BigDecimal.ZERO;
        int leaveDays = summary != null ? summary.getTotalLeaveDays() : 0;
        int halfLeaveDays = summary != null ? summary.getTotalHalfLeaveDays() : 0;
        String status = summary != null ? summary.getStatus() : "NO_DATA";

        return new InternalEmployeeCalendarDto(employeeId, emp.getPublicId(), emp.getEmployeeCode(), emp.getFullName(),
                yearMonth, days, totalExpected, totalRaw, totalAdj, totalRawOt, totalAdjustedOt, totalDays, leaveDays, halfLeaveDays,
                summary != null ? summary.getRevision() : 0L, status);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Confirm / Unconfirm
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public void confirmEmployee(Long companyId, Long employeeId, String yearMonth, Long userId) {
        InternalStatementSummary s = summaryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .orElseThrow(() -> new BusinessException("No data to confirm", HttpStatus.BAD_REQUEST, "NO_DATA"));
        if ("CLOSED".equals(s.getStatus())) {
            throw new BusinessException(
                    "Biên bản nội bộ tháng " + yearMonth + " đã đóng. Không thể xác nhận lại.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }
        s.setStatus("CONFIRMED");
        s.setConfirmedBy(userId);
        s.setConfirmedAt(LocalDateTime.now());
        summaryRepo.save(s);
    }

    @Transactional
    public void confirmAll(Long companyId, String yearMonth, Long userId) {
        List<InternalStatementSummary> summaries = summaryRepo.findByCompanyIdAndYearMonth(companyId, yearMonth);
        // Chặn nếu tháng đã CLOSED
        boolean hasClosed = summaries.stream().anyMatch(s -> "CLOSED".equals(s.getStatus()));
        if (hasClosed) {
            throw new BusinessException(
                    "Biên bản nội bộ tháng " + yearMonth + " đã đóng. Không thể xác nhận lại.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }
        LocalDateTime now = LocalDateTime.now();
        summaries.forEach(s -> {
            s.setStatus("CONFIRMED");
            s.setConfirmedBy(userId);
            s.setConfirmedAt(now);
        });
        summaryRepo.saveAll(summaries);
    }

    @Transactional
    public void unconfirmEmployee(Long companyId, Long employeeId, String yearMonth) {
        summaryRepo.findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .ifPresent(s -> {
                    if ("CLOSED".equals(s.getStatus())) {
                        throw new BusinessException(
                                "Biên bản nội bộ tháng " + yearMonth + " đã đóng. Không thể hủy xác nhận.",
                                HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
                    }
                    s.setStatus("DRAFT");
                    s.setConfirmedBy(null);
                    s.setConfirmedAt(null);
                    summaryRepo.save(s);
                    markPayrollStale(companyId, employeeId, yearMonth);
                });
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Lấy adjusted_days của employee cho billing (nếu CONFIRMED)
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public void reopenEmployee(Long companyId, Long employeeId, String yearMonth, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Phải nhập lý do mở lại Biên bản", HttpStatus.BAD_REQUEST, "REOPEN_REASON_REQUIRED");
        }
        InternalStatementSummary summary = summaryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .orElseThrow(() -> new BusinessException("Không tìm thấy Biên bản nội bộ", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!"CONFIRMED".equals(summary.getStatus())) {
            throw new BusinessException("Chỉ có thể mở lại Biên bản đã xác nhận", HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        markPayrollStale(companyId, employeeId, yearMonth);
        summary.setStatus("DRAFT");
        summary.setConfirmedBy(null);
        summary.setConfirmedAt(null);
        summary.setRevision(summary.getRevision() + 1);
        summaryRepo.save(summary);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getConfirmedAdjustedDays(Long companyId, Long employeeId, String yearMonth) {
        return summaryRepo.findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .filter(s -> "CONFIRMED".equals(s.getStatus()))
                .map(InternalStatementSummary::getTotalAdjustedDays);
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Export Excel
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public byte[] exportExcel(Long companyId, String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Employee> employees = employeeRepo.findByCompanyIdAndDeletedAtIsNull(companyId);
        Map<Long, InternalStatementSummary> summaryMap = summaryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .stream().collect(Collectors.toMap(InternalStatementSummary::getEmployeeId, s -> s));
        Map<Long, List<InternalStatementEntry>> entriesMap = entryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .stream().collect(Collectors.groupingBy(InternalStatementEntry::getEmployeeId));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle leaveFullStyle = createFillStyle(wb, IndexedColors.ROSE);
            CellStyle leaveHalfStyle = createFillStyle(wb, IndexedColors.LIGHT_YELLOW);
            CellStyle weekendStyle = createFillStyle(wb, IndexedColors.GREY_25_PERCENT);
            CellStyle confirmedStyle = createFillStyle(wb, IndexedColors.LIGHT_GREEN);

            // ── Sheet 1: Tổng quan ─────────────────────────────────────────
            Sheet overviewSheet = wb.createSheet("Tổng Quan");
            writeOverviewSheet(overviewSheet, employees, summaryMap, ym, headerStyle, dataStyle, confirmedStyle);

            // ── Sheet per nhân sự ──────────────────────────────────────────
            for (Employee emp : employees) {
                InternalStatementSummary summary = summaryMap.get(emp.getId());
                List<InternalStatementEntry> entries = entriesMap.getOrDefault(emp.getId(), List.of());
                if (entries.isEmpty() && summary == null) continue;

                String sheetName = sanitizeSheetName(emp.getEmployeeCode() + " " + emp.getFullName());
                Sheet sheet = wb.createSheet(sheetName);
                writeEmployeeSheet(sheet, emp, summary, entries, ym,
                        headerStyle, dataStyle, leaveFullStyle, leaveHalfStyle, weekendStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private void recalculateSummary(Long companyId, Long employeeId, String yearMonth) {
        // Bug 2: Không recalculate khi summary đã CLOSED
        Optional<InternalStatementSummary> existing = summaryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth);
        if (existing.isPresent() && !"DRAFT".equals(existing.get().getStatus())) {
            log.warn("Skipped recalculateSummary: statement CLOSED for employee={}, month={}",
                    employeeId, yearMonth);
            return;
        }

        List<InternalStatementEntry> entries = entryRepo
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth);

        // Bug 3: Nếu không còn entry nào, xóa summary
        if (entries.isEmpty()) {
            existing.ifPresent(summaryRepo::delete);
            return;
        }

        BigDecimal totalRaw = BigDecimal.ZERO;
        BigDecimal totalAdj = BigDecimal.ZERO;
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalRawOt = BigDecimal.ZERO;
        BigDecimal totalAdjustedOt = BigDecimal.ZERO;
        int leaveDays = 0;
        int halfLeaveDays = 0;

        for (InternalStatementEntry e : entries) {
            totalRaw = totalRaw.add(e.getRawHours());
            totalAdj = totalAdj.add(e.getAdjustedHours());
            totalExpected = totalExpected.add(e.getExpectedHours());
            totalRawOt = totalRawOt.add(e.getRawOtHours());
            totalAdjustedOt = totalAdjustedOt.add(e.getAdjustedOtHours());
            if (e.isLeaveDay()) leaveDays++;
            if (e.isHalfLeave()) halfLeaveDays++;
        }

        BigDecimal totalDays = STD_HOURS.compareTo(BigDecimal.ZERO) > 0
                ? totalAdj.divide(STD_HOURS, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        InternalStatementSummary s = existing.orElse(InternalStatementSummary.builder()
                .companyId(companyId)
                .employeeId(employeeId)
                .yearMonth(yearMonth)
                .build());

        s.setTotalRawHours(totalRaw);
        s.setTotalAdjustedHours(totalAdj);
        s.setTotalExpectedHours(totalExpected);
        s.setTotalRawOtHours(totalRawOt);
        s.setTotalAdjustedOtHours(totalAdjustedOt);
        s.setTotalAdjustedDays(totalDays);
        s.setTotalLeaveDays(leaveDays);
        s.setTotalHalfLeaveDays(halfLeaveDays);
        if ("DRAFT".equals(s.getStatus())) s.setRevision(s.getRevision() + 1);
        summaryRepo.save(s);
    }

    private String getTimesheetNote(Long companyId, Long employeeId, LocalDate date) {
        Employee emp = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId).orElse(null);
        if (emp == null || emp.getUserId() == null) return null;
        return timesheetRepo.findByUserIdAndWorkDateAndDeletedAtIsNull(emp.getUserId(), date)
                .stream().map(Timesheet::getNote).filter(n -> n != null && !n.isBlank())
                .findFirst().orElse(null);
    }

    private boolean isConfirmed(Long companyId, Long employeeId, String yearMonth) {
        return summaryRepo.findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .map(s -> "CONFIRMED".equals(s.getStatus()) || "CLOSED".equals(s.getStatus()))
                .orElse(false);
    }

    private void markPayrollStale(Long companyId, Long employeeId, String yearMonth) {
        payrollRecordRepository.findByCompanyIdAndEmployeeIdAndPayrollMonth(companyId, employeeId, yearMonth)
                .ifPresent(payroll -> {
                    if ("PAID".equals(payroll.getStatus())) {
                        throw new BusinessException("Payroll đã PAID, không thể mở lại Biên bản", HttpStatus.CONFLICT, "PAYROLL_PAID");
                    }
                    payroll.setStatus("STALE");
                    payrollRecordRepository.save(payroll);
                });
    }

    private InternalCalendarDayDto toCalendarDay(InternalStatementEntry entry, String tsNote) {
        DayOfWeek dow = entry.getWorkDate().getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        return new InternalCalendarDayDto(
                entry.getWorkDate(),
                dow.name().substring(0, 3),
                isWeekend,
                entry.getExpectedHours(),
                entry.getRawHours(),
                entry.getAdjustedHours(),
                entry.getRawOtHours(),
                entry.getAdjustedOtHours(),
                entry.getOtMultiplier(),
                entry.getOtAdjustmentReason(),
                entry.isLeaveDay(),
                entry.isHalfLeave(),
                entry.isPaidLeave(),
                entry.getLeaveTypeName(),
                tsNote,
                entry.getNote(),
                entry.getId()
        );
    }

    // ── Excel helpers ──────────────────────────────────────────────────────────

    private void writeOverviewSheet(Sheet sheet, List<Employee> employees,
                                    Map<Long, InternalStatementSummary> summaryMap, YearMonth ym,
                                    CellStyle headerStyle, CellStyle dataStyle, CellStyle confirmedStyle) {
        String[] cols = {"Mã NV", "Họ và Tên", "Giờ Raw", "Giờ Điều Chỉnh", "Ngày Công", "Ngày Phép", "Nửa Ngày Phép", "Trạng Thái"};
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("BIÊN BẢN NỘI BỘ - THÁNG " + ym.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols.length - 1));

        Row header = sheet.createRow(1);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 2;
        BigDecimal totalAdj = BigDecimal.ZERO;
        for (Employee emp : employees) {
            InternalStatementSummary s = summaryMap.get(emp.getId());
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(emp.getEmployeeCode());
            row.createCell(1).setCellValue(emp.getFullName());
            if (s != null) {
                row.createCell(2).setCellValue(s.getTotalRawHours().doubleValue());
                row.createCell(3).setCellValue(s.getTotalAdjustedHours().doubleValue());
                row.createCell(4).setCellValue(s.getTotalAdjustedDays().doubleValue());
                row.createCell(5).setCellValue(s.getTotalLeaveDays());
                row.createCell(6).setCellValue(s.getTotalHalfLeaveDays());
                Cell statusCell = row.createCell(7);
                statusCell.setCellValue("CONFIRMED".equals(s.getStatus()) ? "Đã xác nhận" : "Nháp");
                if ("CONFIRMED".equals(s.getStatus())) statusCell.setCellStyle(confirmedStyle);
                totalAdj = totalAdj.add(s.getTotalAdjustedDays());
            } else {
                row.createCell(7).setCellValue("Chưa có dữ liệu");
            }
            for (int i = 0; i < 7; i++) {
                if (row.getCell(i) != null) row.getCell(i).setCellStyle(dataStyle);
            }
        }

        // Footer
        Row footer = sheet.createRow(rowIdx + 1);
        footer.createCell(1).setCellValue("TỔNG");
        footer.createCell(4).setCellValue(totalAdj.doubleValue());

        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private void writeEmployeeSheet(Sheet sheet, Employee emp, InternalStatementSummary summary,
                                    List<InternalStatementEntry> entries, YearMonth ym,
                                    CellStyle headerStyle, CellStyle dataStyle,
                                    CellStyle leaveFullStyle, CellStyle leaveHalfStyle, CellStyle weekendStyle) {
        Map<LocalDate, InternalStatementEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(InternalStatementEntry::getWorkDate, e -> e));

        String[] cols = {"Ngày", "Thứ", "Giờ Log", "Loại Phép", "Giờ Điều Chỉnh", "Ghi Chú"};

        // Title
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(emp.getFullName() + " (" + emp.getEmployeeCode() + ")");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols.length - 1));

        Row subTitle = sheet.createRow(1);
        subTitle.createCell(0).setCellValue("Biên Bản Nội Bộ - Tháng " + ym.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols.length - 1));

        // Header
        Row header = sheet.createRow(3);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }

        // Days
        int rowIdx = 4;
        String[] dayNames = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        LocalDate cur = ym.atDay(1);
        while (!cur.isAfter(ym.atEndOfMonth())) {
            InternalStatementEntry entry = entryMap.get(cur);
            DayOfWeek dow = cur.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            String dowLabel = dayNames[dow.getValue() % 7];

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(cur.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            row.createCell(1).setCellValue(dowLabel);

            CellStyle rowStyle = dataStyle;
            if (entry != null) {
                row.createCell(2).setCellValue(entry.getRawHours().doubleValue());
                String leaveLabel = "";
                if (entry.isLeaveDay()) {
                    leaveLabel = "Nghỉ phép" + (entry.isPaidLeave() ? " (có lương)" : " (không lương)");
                    rowStyle = leaveFullStyle;
                } else if (entry.isHalfLeave()) {
                    leaveLabel = "Nửa ngày phép" + (entry.isPaidLeave() ? " (có lương)" : " (không lương)");
                    rowStyle = leaveHalfStyle;
                } else if (isWeekend) {
                    rowStyle = weekendStyle;
                }
                if (entry.getLeaveTypeName() != null) leaveLabel += " - " + entry.getLeaveTypeName();
                row.createCell(3).setCellValue(leaveLabel);
                row.createCell(4).setCellValue(entry.getAdjustedHours().doubleValue());
                row.createCell(5).setCellValue(entry.getNote() != null ? entry.getNote() : "");
            } else {
                row.createCell(2).setCellValue(0);
                row.createCell(3).setCellValue("");
                row.createCell(4).setCellValue(0);
                row.createCell(5).setCellValue("");
                if (isWeekend) rowStyle = weekendStyle;
            }

            for (int i = 0; i < cols.length; i++) {
                if (row.getCell(i) == null) row.createCell(i);
                row.getCell(i).setCellStyle(rowStyle);
            }
            cur = cur.plusDays(1);
        }

        // Summary
        if (summary != null) {
            rowIdx++;
            Row sumHeader = sheet.createRow(rowIdx++);
            sumHeader.createCell(0).setCellValue("Tổng kết");
            sumHeader.getCell(0).setCellStyle(headerStyle);

            Row r1 = sheet.createRow(rowIdx++);
            r1.createCell(0).setCellValue("Tổng giờ log:");
            r1.createCell(1).setCellValue(summary.getTotalRawHours().doubleValue());

            Row r2 = sheet.createRow(rowIdx++);
            r2.createCell(0).setCellValue("Tổng giờ điều chỉnh:");
            r2.createCell(1).setCellValue(summary.getTotalAdjustedHours().doubleValue());

            Row r3 = sheet.createRow(rowIdx++);
            r3.createCell(0).setCellValue("Tổng ngày công:");
            r3.createCell(1).setCellValue(summary.getTotalAdjustedDays().doubleValue());

            Row r4 = sheet.createRow(rowIdx++);
            r4.createCell(0).setCellValue("Ngày phép (cả ngày):");
            r4.createCell(1).setCellValue(summary.getTotalLeaveDays());

            Row r5 = sheet.createRow(rowIdx);
            r5.createCell(0).setCellValue("Ngày phép (nửa ngày):");
            r5.createCell(1).setCellValue(summary.getTotalHalfLeaveDays());
        }

        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle createFillStyle(Workbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private String sanitizeSheetName(String name) {
        String s = name.replaceAll("[\\\\/*?\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    @Transactional
    public void closeMonth(Long companyId, String yearMonth, Long closedBy, String reason) {
        // Set all summaries to CLOSED
        List<InternalStatementSummary> summaries = summaryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth);
        // Chặn nếu đã đóng
        boolean alreadyClosed = summaries.stream().allMatch(s -> "CLOSED".equals(s.getStatus()));
        if (alreadyClosed && !summaries.isEmpty()) {
            throw new BusinessException(
                    "Biên bản nội bộ tháng " + yearMonth + " đã được đóng trước đó.",
                    HttpStatus.BAD_REQUEST, "ALREADY_CLOSED");
        }
        for (InternalStatementSummary s : summaries) {
            if (!"CONFIRMED".equals(s.getStatus()) && !"CLOSED".equals(s.getStatus())) {
                throw new BusinessException(
                        "Tất cả biên bản nội bộ phải ở trạng thái CONFIRMED trước khi đóng. " +
                        "Nhân sự chưa xác nhận: employeeId=" + s.getEmployeeId(),
                        HttpStatus.BAD_REQUEST, "NOT_ALL_CONFIRMED");
            }
            s.setStatus("CLOSED");
        }
        summaryRepo.saveAll(summaries);

        // Lock timesheet
        TimesheetPeriodLock lock = timesheetPeriodLockRepository
                .findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseGet(() -> TimesheetPeriodLock.builder()
                        .companyId(companyId).yearMonth(yearMonth).build());
        lock.setStatementClosed(true);
        lock.setClosedBy(closedBy);
        lock.setClosedAt(LocalDateTime.now());
        lock.setClosedReason(reason != null ? reason : "Đóng biên bản nội bộ");
        timesheetPeriodLockRepository.save(lock);

        log.info("Internal statement CLOSED for month={}, companyId={}, by={}", yearMonth, companyId, closedBy);
    }

    @Transactional
    public void reopenMonth(Long companyId, String yearMonth, Long reopenBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Phải nhập lý do mở lại", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }

        List<InternalStatementSummary> summaries = summaryRepo
                .findByCompanyIdAndYearMonth(companyId, yearMonth);
        for (InternalStatementSummary s : summaries) {
            if ("CLOSED".equals(s.getStatus())) {
                s.setStatus("CONFIRMED");
            }
        }
        summaryRepo.saveAll(summaries);

        // Unlock timesheet
        timesheetPeriodLockRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .ifPresent(lock -> {
                    lock.setStatementClosed(false);
                    lock.setReopenReason(reason);
                    timesheetPeriodLockRepository.save(lock);
                });

        log.info("Internal statement REOPENED for month={}, companyId={}, by={}, reason={}",
                yearMonth, companyId, reopenBy, reason);
    }
}
