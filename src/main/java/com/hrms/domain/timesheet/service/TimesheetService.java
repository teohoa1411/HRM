package com.hrms.domain.timesheet.service;

import com.hrms.domain.calendar.service.WorkCalendarService;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.payroll.repository.PayrollRecordRepository;
import com.hrms.domain.internal_statement.repository.InternalStatementSummaryRepository;
import com.hrms.domain.statement.entity.Statement;
import com.hrms.domain.statement.entity.StatementHistory;
import com.hrms.domain.statement.repository.StatementHistoryRepository;
import com.hrms.domain.statement.repository.StatementLineItemRepository;
import com.hrms.domain.statement.repository.StatementRepository;
import com.hrms.domain.timesheet.dto.*;
import com.hrms.domain.timesheet.entity.Timesheet;
import com.hrms.domain.timesheet.entity.TimesheetMonthly;
import com.hrms.domain.timesheet.entity.TimesheetPeriodLock;
import com.hrms.domain.timesheet.repository.TimesheetMonthlyRepository;
import com.hrms.domain.timesheet.repository.TimesheetPeriodLockRepository;
import com.hrms.domain.timesheet.repository.TimesheetRepository;
import lombok.RequiredArgsConstructor;
import com.hrms.infrastructure.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetRepository repo;
    private final TimesheetMonthlyRepository monthlyRepo;
    private final TimesheetPeriodLockRepository lockRepo;
    private final WorkCalendarService workCalendarService;
    private final PayrollRecordRepository payrollRecordRepository;
    private final StatementLineItemRepository statementLineItemRepository;
    private final StatementRepository statementRepository;
    private final StatementHistoryRepository statementHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final InternalStatementSummaryRepository internalStatementSummaryRepository;

    // ── Helpers ──────────────────────────────────────────

    /** Kiểm tra tháng có bị khóa không. Trả về Optional chứa lock nếu đang locked. */
    private Optional<TimesheetPeriodLock> getActiveLock(Long companyId, String yearMonth) {
        return lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .filter(TimesheetPeriodLock::isLocked);
    }

    /**
     * Ném BusinessException nếu tháng đã bị khóa (tầng 1 - Period Lock).
     * isAdmin = true → bỏ qua kiểm tra (quản lý vẫn được sửa).
     */
    private void assertMonthNotLocked(Long companyId, String yearMonth, boolean isAdmin) {
        if (isAdmin) return;
        if (lockRepo.existsByCompanyIdAndYearMonthAndLockedTrue(companyId, yearMonth)) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa. Vui lòng liên hệ quản lý.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_MONTH_LOCKED");
        }
    }

    /**
     * Multi-level lock validation (spec section 10.4).
     * Kiểm tra cả 3 tầng khóa: Period Lock, Payroll Lock, Statement Lock.
     * userId = user đang thao tác (để lookup employeeId).
     * isAdmin = true → bỏ qua kiểm tra.
     */
    private void assertEditAllowed(Long companyId, Long userId, String yearMonth, boolean isAdmin) {
        Optional<TimesheetPeriodLock> lockOpt = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth);

        // Tầng -1: Statement CLOSED — KHÓA TUYỆT ĐỐI, kể cả COMPANY_ADMIN.
        // Chỉ COMPANY_ADMIN mở lại biên bản (reopenStatement) mới unlock.
        if (lockOpt.isPresent() && lockOpt.get().isStatementClosed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa do biên bản đã đóng. " +
                    "Chỉ Quản lý công ty (COMPANY_ADMIN) mới có thể mở lại biên bản.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        // Tầng 0: Accountant Confirmed — KHÓA HOÀN TOÀN, kể cả admin.
        // Muốn sửa phải revert xác nhận kế toán trước (POST .../accountant-revert-confirm).
        if (lockOpt.isPresent() && lockOpt.get().isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã được Kế toán xác nhận và khóa hoàn toàn. " +
                    "Kế toán cần Hủy xác nhận trước khi sửa.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_ACCOUNTANT_CONFIRMED");
        }

        Long employeeId = employeeRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(e -> e.getId()).orElse(null);
        if (employeeId != null && internalStatementSummaryRepository
                .findByCompanyIdAndEmployeeIdAndYearMonth(companyId, employeeId, yearMonth)
                .map(s -> "CONFIRMED".equals(s.getStatus())).orElse(false)) {
            throw new BusinessException("Biên bản nội bộ đã xác nhận; hãy mở lại trước khi sửa chấm công",
                    HttpStatus.FORBIDDEN, "INTERNAL_STATEMENT_CONFIRMED");
        }
        if (isAdmin) return;

        // Tầng 1: Period Lock
        if (lockRepo.existsByCompanyIdAndYearMonthAndLockedTrue(companyId, yearMonth)) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa. Vui lòng liên hệ quản lý.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_MONTH_LOCKED");
        }

        // Lookup employeeId từ userId
        if (employeeId == null) return;

        // Tầng 2: Payroll Lock — PayrollRecord đã CONFIRMED hoặc EXPORTED
        if (payrollRecordRepository.existsByCompanyIdAndEmployeeIdAndPayrollMonthAndStatusIn(
                companyId, employeeId, yearMonth, List.of("CONFIRMED", "EXPORTED"))) {
            throw new BusinessException(
                    "Tháng " + yearMonth + " đã có bảng lương được xác nhận. Liên hệ COMPANY_ADMIN để mở khóa.",
                    HttpStatus.FORBIDDEN, "PAYROLL_ALREADY_CONFIRMED");
        }

        // Tầng 3: Statement Lock — Biên bản đã SENT hoặc CONFIRMED
        if (!statementLineItemRepository.findSentByEmployeeAndMonth(companyId, employeeId, yearMonth).isEmpty()) {
            throw new BusinessException(
                    "Tháng " + yearMonth + " đã có biên bản được gửi cho khách hàng/vendor. Liên hệ COMPANY_ADMIN để mở khóa.",
                    HttpStatus.FORBIDDEN, "STATEMENT_ALREADY_SENT");
        }
    }

    /**
     * Trả về trạng thái tất cả các tầng khóa cho userId + tháng.
     * Dùng cho API GET /v1/timesheets/{yearMonth}/lock-status
     */
    public Map<String, Object> getLockStatusAllLevels(Long companyId, Long userId, String yearMonth) {
        Optional<TimesheetPeriodLock> lockOpt = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth);
        boolean periodLocked = lockOpt.map(TimesheetPeriodLock::isLocked).orElse(false);
        boolean accountantConfirmed = lockOpt.map(TimesheetPeriodLock::isAccountantConfirmed).orElse(false);

        Long employeeId = employeeRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(emp -> emp.getId()).orElse(null);

        boolean payrollLocked = employeeId != null &&
                payrollRecordRepository.existsByCompanyIdAndEmployeeIdAndPayrollMonthAndStatusIn(
                        companyId, employeeId, yearMonth, List.of("CONFIRMED", "EXPORTED"));
        boolean statementLocked = employeeId != null &&
                !statementLineItemRepository.findSentByEmployeeAndMonth(companyId, employeeId, yearMonth).isEmpty();

        return Map.of(
                "yearMonth", yearMonth,
                "periodLocked", periodLocked,
                "accountantConfirmed", accountantConfirmed,
                "payrollLocked", payrollLocked,
                "statementLocked", statementLocked,
                "locked", periodLocked || payrollLocked || statementLocked || accountantConfirmed
        );
    }

    private String toYearMonth(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    // ── Entries ──────────────────────────────────────────

    public List<TimesheetEntryResponse> getMonth(Long userId, Long companyId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();
        return repo.findByUserAndMonth(userId, companyId, from, to)
                   .stream().map(TimesheetEntryResponse::from).toList();
    }

    /**
     * Bulk upsert entries cho employee (check lock).
     * isAdmin = true để admin bypass month lock.
     */
    @Transactional
    public List<TimesheetEntryResponse> bulkSave(List<TimesheetEntryRequest> entries,
                                                  Long userId, Long companyId,
                                                  boolean isAdmin) {
        return entries.stream()
                .map(req -> upsert(req, userId, companyId, isAdmin))
                .filter(r -> r != null)
                .toList();
    }

    private TimesheetEntryResponse upsert(TimesheetEntryRequest req,
                                           Long userId, Long companyId,
                                           boolean isAdmin) {
        String yearMonth = toYearMonth(req.workDate());

        // 1. Kiểm tra tất cả các tầng khóa (Period + Payroll + Statement)
        assertEditAllowed(companyId, userId, yearMonth, isAdmin);

        boolean noHours = req.hours().compareTo(BigDecimal.ZERO) == 0
                && (req.otHours() == null || req.otHours().compareTo(BigDecimal.ZERO) == 0);

        if (noHours) {
            repo.findByUserIdAndProjectKeyAndWorkDateAndDeletedAtIsNull(
                    userId, req.projectKey(), req.workDate())
                .ifPresent(t -> {
                    // 2. Không cho xóa entry bị khóa bởi leave (trừ admin)
                    if (!isAdmin && t.isLockedByLeave()) {
                        throw new BusinessException(
                                "Ngày " + req.workDate() + " đang bị khóa bởi đơn nghỉ phép đã duyệt.",
                                HttpStatus.FORBIDDEN, "TIMESHEET_LEAVE_LOCKED");
                    }
                    t.softDelete();
                    repo.save(t);
                });
            return null;
        }

        // 3. Chỉ validate ngày làm việc khi thực sự log giờ
        if (!workCalendarService.isWorkday(companyId, req.workDate())) {
            throw new BusinessException("Ngày " + req.workDate() + " không phải ngày làm việc theo lịch đã cấu hình");
        }

        Timesheet t = repo.findByUserIdAndProjectKeyAndWorkDateAndDeletedAtIsNull(
                        userId, req.projectKey(), req.workDate())
                .orElseGet(() -> Timesheet.builder()
                        .userId(userId)
                        .companyId(companyId)
                        .projectKey(req.projectKey().toUpperCase().trim())
                        .workDate(req.workDate())
                        .build());

        // 4. Không cho sửa entry bị khóa bởi leave (trừ admin)
        if (!isAdmin && t.isLockedByLeave()) {
            throw new BusinessException(
                    "Ngày " + req.workDate() + " đang bị khóa bởi đơn nghỉ phép đã duyệt.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_LEAVE_LOCKED");
        }

        t.setHours(req.hours());
        t.setOtHours(req.otHours() != null ? req.otHours() : BigDecimal.ZERO);
        t.setNote(req.note());
        if (req.projectId() != null && !req.projectId().isBlank()) {
            t.setProjectId(req.projectId().trim());
        }
        if (req.projectName() != null && !req.projectName().isBlank()) {
            t.setProjectName(req.projectName().trim());
        }
        TimesheetEntryResponse result = TimesheetEntryResponse.from(repo.save(t));
        // Đánh dấu STALE các biên bản đã xuất nếu timesheet bị chỉnh sau khi xuất
        markRelatedStatementsStale(companyId, userId, yearMonth, req.workDate());
        return result;
    }

    @Transactional
    public void delete(Long id, Long companyId, boolean isAdmin) {
        Timesheet t = repo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Timesheet entry not found"));

        // Chặn EMPLOYEE xóa timesheet của người khác
        if (!isAdmin && !t.getUserId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("Không có quyền xóa timesheet của người khác",
                    HttpStatus.FORBIDDEN, "NOT_OWNER");
        }

        String yearMonth = toYearMonth(t.getWorkDate());
        assertEditAllowed(companyId, t.getUserId(), yearMonth, isAdmin);

        if (!isAdmin && t.isLockedByLeave()) {
            throw new BusinessException(
                    "Ngày " + t.getWorkDate() + " đang bị khóa bởi đơn nghỉ phép đã duyệt.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_LEAVE_LOCKED");
        }

        t.softDelete();
        repo.save(t);
        // Đánh dấu STALE các biên bản đã xuất nếu timesheet bị xóa sau khi xuất
        markRelatedStatementsStale(companyId, t.getUserId(), yearMonth, t.getWorkDate());
    }

    /**
     * Đánh dấu STALE tất cả biên bản (EXPORTED/SENT/CONFIRMED) có line item của employee này,
     * được gọi khi timesheet bị chỉnh sửa sau khi biên bản đã xuất.
     */
    private void markRelatedStatementsStale(Long companyId, Long userId, String yearMonth, LocalDate workDate) {
        Long employeeId = employeeRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(e -> e.getId()).orElse(null);
        if (employeeId == null) return;

        List<Statement> toStale = statementRepository.findExportedByEmployeeAndMonth(companyId, employeeId, yearMonth);
        if (toStale.isEmpty()) return;

        String reason = "Timesheet ngày " + workDate + " bị chỉnh sửa sau khi xuất biên bản";
        for (Statement stmt : toStale) {
            String prevStatus = stmt.getStatus();
            stmt.markStale(reason);
            statementRepository.save(stmt);
            statementHistoryRepository.save(
                    StatementHistory.markedStale(stmt.getId(), prevStatus, 0L, reason));
        }
    }

    /** Xóa toàn bộ entries của 1 project trong 1 tháng (soft delete). */
    @Transactional
    public int deleteProject(Long userId, Long companyId, String projectKey, YearMonth month, boolean isAdmin) {
        String yearMonth = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
        assertMonthNotLocked(companyId, yearMonth, isAdmin);

        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();
        List<Timesheet> entries = repo.findByUserProjectAndMonth(userId, companyId, projectKey.toUpperCase(), from, to);
        for (Timesheet t : entries) {
            if (!isAdmin && t.isLockedByLeave()) {
                throw new BusinessException(
                        "Project " + projectKey + " có ngày bị khóa bởi đơn nghỉ phép. Không thể xóa toàn bộ.",
                        HttpStatus.FORBIDDEN, "TIMESHEET_LEAVE_LOCKED");
            }
            t.softDelete();
            repo.save(t);
        }
        return entries.size();
    }

    // ── Internal: fill timesheet khi leave được duyệt (gọi từ LeaveService) ──

    /**
     * Tạo/cập nhật timesheet entry với projectKey="LEAVE" cho từng ngày nghỉ.
     * Gọi khi leave request được APPROVED.
     * Ném lỗi nếu tháng đã bị khóa.
     *
     * @param userId     userId của nhân sự
     * @param companyId  companyId
     * @param date       ngày nghỉ cụ thể
     * @param hours      0 = cả ngày, 4 = nửa ngày
     */
    @Transactional
    public void fillLeaveTimesheet(Long userId, Long companyId, LocalDate date, BigDecimal hours) {
        String yearMonth = toYearMonth(date);

        // Kiểm tra biên bản đã đóng → chặn hoàn toàn
        Optional<TimesheetPeriodLock> lockOpt = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth);
        if (lockOpt.isPresent() && lockOpt.get().isStatementClosed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa do biên bản đã đóng. Không thể cập nhật nghỉ phép.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        // Quản lý approve leave nhưng tháng đã khóa → báo lỗi
        if (lockRepo.existsByCompanyIdAndYearMonthAndLockedTrue(companyId, yearMonth)) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa. Không thể cập nhật timesheet nghỉ phép.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_MONTH_LOCKED");
        }

        Timesheet t = repo.findByUserIdAndProjectKeyAndWorkDateAndDeletedAtIsNull(
                        userId, "LEAVE", date)
                .orElseGet(() -> Timesheet.builder()
                        .userId(userId)
                        .companyId(companyId)
                        .projectKey("LEAVE")
                        .workDate(date)
                        .otHours(BigDecimal.ZERO)
                        .build());

        t.setHours(hours);
        t.setLockedByLeave(true);
        t.setNote("Nghỉ phép đã duyệt");
        repo.save(t);
    }

    /**
     * Gỡ lock leave trên timesheet khi leave bị huỷ/từ chối (reset entry về trạng thái editable).
     * Xóa luôn entry nếu hours = 0.
     */
    @Transactional
    public void releaseLeaveTimesheet(Long userId, Long companyId, LocalDate date) {
        // Kiểm tra biên bản đã đóng → chặn
        String yearMonth = toYearMonth(date);
        Optional<TimesheetPeriodLock> lockOpt = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth);
        if (lockOpt.isPresent() && lockOpt.get().isStatementClosed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa do biên bản đã đóng. Không thể hủy nghỉ phép.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        repo.findByUserIdAndProjectKeyAndWorkDateAndDeletedAtIsNull(userId, "LEAVE", date)
                .ifPresent(t -> {
                    t.softDelete();
                    repo.save(t);
                });
    }

    // ── Monthly metadata ──────────────────────────────────

    public TimesheetMonthlyResponse getMeta(Long userId, Long companyId, String yearMonth) {
        Optional<TimesheetPeriodLock> lock = getActiveLock(companyId, yearMonth);
        boolean isLocked    = lock.isPresent();
        Long lockedBy       = lock.map(TimesheetPeriodLock::getLockedBy).orElse(null);
        LocalDateTime lockedAt = lock.map(TimesheetPeriodLock::getLockedAt).orElse(null);

        return monthlyRepo.findByUserIdAndYearMonth(userId, yearMonth)
                .map(m -> TimesheetMonthlyResponse.from(m, isLocked, lockedBy, lockedAt))
                .orElse(TimesheetMonthlyResponse.empty(yearMonth, isLocked, lockedBy, lockedAt));
    }

    @Transactional
    public TimesheetMonthlyResponse saveMeta(Long userId, Long companyId,
                                              String yearMonth, TimesheetMonthlyRequest req,
                                              boolean isAdmin) {
        assertMonthNotLocked(companyId, yearMonth, isAdmin);

        TimesheetMonthly m = monthlyRepo.findByUserIdAndYearMonth(userId, yearMonth)
                .orElseGet(() -> TimesheetMonthly.builder()
                        .userId(userId).companyId(companyId).yearMonth(yearMonth).build());
        m.setDescription(req.description());
        m.setAdditions(req.additions());

        Optional<TimesheetPeriodLock> lock = getActiveLock(companyId, yearMonth);
        boolean isLocked = lock.isPresent();
        Long lockedBy    = lock.map(TimesheetPeriodLock::getLockedBy).orElse(null);
        LocalDateTime lockedAt = lock.map(TimesheetPeriodLock::getLockedAt).orElse(null);

        return TimesheetMonthlyResponse.from(monthlyRepo.save(m), isLocked, lockedBy, lockedAt);
    }

    // ── Lock / Unlock ─────────────────────────────────────

    /**
     * Khóa timesheet tháng yearMonth cho toàn bộ nhân sự trong công ty.
     * Sau khi khóa, nhân sự không thể sửa timesheet; chỉ COMPANY_ADMIN được sửa.
     */
    @Transactional
    public TimesheetLockResponse lockMonth(Long companyId, String yearMonth, Long adminUserId) {
        TimesheetPeriodLock lock = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseGet(() -> TimesheetPeriodLock.builder()
                        .companyId(companyId).yearMonth(yearMonth).build());

        if (lock.isLocked()) {
            throw new BusinessException("Timesheet tháng " + yearMonth + " đã được khóa trước đó.");
        }

        lock.setLocked(true);
        lock.setLockedBy(adminUserId);
        lock.setLockedAt(LocalDateTime.now());
        return TimesheetLockResponse.from(lockRepo.save(lock));
    }

    /**
     * Mở khóa timesheet tháng để nhân sự có thể chỉnh sửa lại.
     */
    @Transactional
    public TimesheetLockResponse unlockMonth(Long companyId, String yearMonth) {
        TimesheetPeriodLock lock = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseThrow(() -> new BusinessException("Timesheet tháng " + yearMonth + " chưa được khóa."));

        if (lock.isStatementClosed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa do biên bản đã đóng. " +
                    "Chỉ Quản lý công ty mở lại biên bản trước khi unlock.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        if (lock.isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã được Kế toán xác nhận. " +
                    "Kế toán cần Hủy xác nhận trước khi HR có thể mở khóa.",
                    HttpStatus.FORBIDDEN, "TIMESHEET_ACCOUNTANT_CONFIRMED");
        }

        lock.setLocked(false);
        lock.setLockedBy(null);
        lock.setLockedAt(null);
        return TimesheetLockResponse.from(lockRepo.save(lock));
    }

    /**
     * Kế toán xác nhận timesheet (bước 2 sau khi HR khóa).
     * Điều kiện: tháng đã được HR khóa (locked = true).
     * Sau khi xác nhận: KHÔNG AI sửa được timesheet — kể cả COMPANY_ADMIN.
     * Sau bước này mới cho phép tạo biên bản nội bộ và biên bản vendor.
     * Biên bản khách hàng chỉ cần HR khóa timesheet.
     */
    @Transactional
    public TimesheetLockResponse confirmByAccountant(Long companyId, String yearMonth, Long accountantUserId) {
        TimesheetPeriodLock lock = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseThrow(() -> new BusinessException(
                        "Timesheet tháng " + yearMonth + " chưa được HR khóa. " +
                        "HR cần khóa timesheet trước khi Kế toán xác nhận.",
                        HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_LOCKED_BY_HR"));

        if (!lock.isLocked()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " chưa được HR khóa.",
                    HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_LOCKED_BY_HR");
        }

        if (lock.isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã được Kế toán xác nhận trước đó.",
                    HttpStatus.BAD_REQUEST, "TIMESHEET_ALREADY_CONFIRMED");
        }

        lock.setAccountantConfirmed(true);
        lock.setAccountantConfirmedBy(accountantUserId);
        lock.setAccountantConfirmedAt(LocalDateTime.now());
        lock.setAccountantRevertReason(null);
        return TimesheetLockResponse.from(lockRepo.save(lock));
    }

    /**
     * Kế toán Hủy xác nhận timesheet (revert), đưa trạng thái từ CONFIRMED_BY_ACCOUNTANT → LOCKED_BY_HR.
     * Yêu cầu bắt buộc lý do (min 20 ký tự) để audit.
     * Không được revert nếu còn Biên bản KH/Vendor đang ở trạng thái CONFIRMED/SENT dựa trên tháng đó.
     */
    @Transactional
    public TimesheetLockResponse revertAccountantConfirm(Long companyId, String yearMonth,
                                                         String reason, Long accountantUserId) {
        if (reason == null || reason.trim().length() < 20) {
            throw new BusinessException(
                    "Lý do hủy xác nhận phải có ít nhất 20 ký tự.",
                    HttpStatus.BAD_REQUEST, "REVERT_REASON_REQUIRED");
        }

        TimesheetPeriodLock lock = lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .orElseThrow(() -> new BusinessException(
                        "Timesheet tháng " + yearMonth + " chưa tồn tại lock.",
                        HttpStatus.NOT_FOUND, "TIMESHEET_LOCK_NOT_FOUND"));

        if (lock.isStatementClosed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " đã bị khóa do biên bản đã đóng. " +
                    "Chỉ Quản lý công ty mở lại biên bản trước.",
                    HttpStatus.FORBIDDEN, "STATEMENT_CLOSED");
        }

        if (!lock.isAccountantConfirmed()) {
            throw new BusinessException(
                    "Timesheet tháng " + yearMonth + " chưa được Kế toán xác nhận.",
                    HttpStatus.BAD_REQUEST, "TIMESHEET_NOT_ACCOUNTANT_CONFIRMED");
        }

        // Không revert được nếu còn biên bản KH/Vendor đã SENT/CONFIRMED
        List<Statement> blockers = statementRepository
                .findByCompanyIdAndStatementMonthAndStatusIn(
                        companyId, yearMonth, List.of("SENT", "CONFIRMED"));
        if (!blockers.isEmpty()) {
            throw new BusinessException(
                    "Có " + blockers.size() + " biên bản khách hàng/vendor đang ở trạng thái SENT/CONFIRMED. " +
                    "Vui lòng hủy xác nhận (reset về DRAFT) các biên bản đó trước.",
                    HttpStatus.CONFLICT, "STATEMENTS_BLOCK_REVERT");
        }

        lock.setAccountantConfirmed(false);
        lock.setAccountantConfirmedBy(null);
        lock.setAccountantConfirmedAt(null);
        lock.setAccountantRevertReason(reason.trim());
        return TimesheetLockResponse.from(lockRepo.save(lock));
    }

    /**
     * Lấy trạng thái lock của tháng.
     */
    @Transactional(readOnly = true)
    public TimesheetLockResponse getLockStatus(Long companyId, String yearMonth) {
        return lockRepo.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .map(TimesheetLockResponse::from)
                .orElse(TimesheetLockResponse.unlocked(companyId, yearMonth));
    }

    // ── Admin overview ────────────────────────────────────

    public List<EmployeeTimesheetSummary> adminOverview(Long companyId, YearMonth month) {
        LocalDate from    = month.atDay(1);
        LocalDate to      = month.atEndOfMonth();
        String yearMonth  = month.toString();
        boolean isLocked  = lockRepo.existsByCompanyIdAndYearMonthAndLockedTrue(companyId, yearMonth);
        if (isLocked) {
            // Tháng đã khóa: chỉ hiện người đã log (giữ nguyên trạng thái lúc khóa)
            return repo.adminOverviewLocked(companyId, from, to, yearMonth);
        } else {
            // Tháng chưa khóa: hiện người đã log + nhân sự active onboard trước cuối tháng
            return repo.adminOverviewUnlocked(companyId, from, to, yearMonth);
        }
    }
}
