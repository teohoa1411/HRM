package com.hrms.domain.leave.service;

import com.hrms.domain.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.leave.dto.*;
import com.hrms.domain.leave.entity.LeaveBalance;
import com.hrms.domain.leave.entity.LeaveRequest;
import com.hrms.domain.leave.entity.LeaveRequest.HalfDayPeriod;
import com.hrms.domain.leave.entity.LeaveRequest.LeaveStatus;
import com.hrms.domain.leave.entity.LeaveType;
import com.hrms.domain.leave.repository.LeaveBalanceRepository;
import com.hrms.domain.leave.repository.LeaveRequestRepository;
import com.hrms.domain.leave.repository.LeaveTypeRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository typeRepo;
    private final LeaveRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final LeaveBalanceRepository balanceRepo;

    // ══════════════════════════════════════════════════════════════
    // Leave Types
    // ══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getTypes(Long companyId) {
        return typeRepo.findByCompanyId(companyId).stream().map(this::toTypeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getActiveTypes(Long companyId) {
        return typeRepo.findByCompanyIdAndActiveTrue(companyId).stream().map(this::toTypeResponse).toList();
    }

    @Transactional
    public LeaveTypeResponse createType(Long companyId, LeaveTypeRequest request) {
        if (typeRepo.existsByCodeAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Leave type code already exists: " + request.code());
        }
        LeaveType lt = LeaveType.builder()
                .companyId(companyId).code(request.code()).name(request.name())
                .maxDays(request.maxDays()).paid(request.paid()).active(true)
                .autoGrantOnCreate(request.autoGrantOnCreate())
                .grantRounding(request.grantRounding() != null ? request.grantRounding() : "HALF_UP")
                .build();
        return toTypeResponse(typeRepo.save(lt));
    }

    @Transactional
    public LeaveTypeResponse updateType(Long id, Long companyId, LeaveTypeRequest request) {
        LeaveType lt = typeRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("LeaveType", id));
        lt.setName(request.name());
        lt.setMaxDays(request.maxDays());
        lt.setPaid(request.paid());
        lt.setAutoGrantOnCreate(request.autoGrantOnCreate());
        if (request.grantRounding() != null) {
            lt.setGrantRounding(request.grantRounding());
        }
        return toTypeResponse(typeRepo.save(lt));
    }

    private LeaveTypeResponse toTypeResponse(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getCompanyId(), t.getCode(),
                t.getName(), t.getMaxDays(), t.isPaid(), t.isActive(),
                t.isAutoGrantOnCreate(), t.getGrantRounding());
    }

    // ══════════════════════════════════════════════════════════════
    // Tính totalDays server-side
    // ══════════════════════════════════════════════════════════════

    private BigDecimal calculateTotalDays(LocalDate startDate, LocalDate endDate, boolean halfDay) {
        if (halfDay) {
            if (!startDate.equals(endDate)) {
                throw new BusinessException("Nghỉ nửa ngày chỉ áp dụng cho 1 ngày (startDate phải bằng endDate).");
            }
            return new BigDecimal("0.5");
        }
        long days = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        return BigDecimal.valueOf(days);
    }

    private void validateHalfDayPeriod(boolean halfDay, HalfDayPeriod halfDayPeriod) {
        if (halfDay && halfDayPeriod == null) {
            throw new BusinessException("Vui lòng chọn nửa buổi sáng (MORNING) hoặc chiều (AFTERNOON) khi nghỉ nửa ngày.");
        }
        if (!halfDay && halfDayPeriod != null) {
            throw new BusinessException("Không thể chỉ định nửa buổi khi nghỉ cả ngày.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Leave Requests
    // ══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> listMyRequests(Long userId, String status, Pageable pageable) {
        Employee emp = employeeRepo.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile not found"));
        Long companyId   = emp.getCompanyId();
        Long employeeId  = emp.getId();
        Page<LeaveRequest> page = (status != null)
                ? requestRepo.findByCompanyIdAndEmployeeIdAndStatusAndDeletedAtIsNull(
                        companyId, employeeId, LeaveStatus.valueOf(status), pageable)
                : requestRepo.findByCompanyIdAndEmployeeIdAndDeletedAtIsNull(companyId, employeeId, pageable);
        return page.map(r -> toResponse(r, companyId));
    }

    @Transactional
    public LeaveRequestResponse createMyRequest(Long userId, CreateLeaveRequest req) {
        Employee emp = employeeRepo.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile not found"));
        return createRequest(emp.getCompanyId(), emp.getId(), req);
    }

    @Transactional(readOnly = true)
    public List<LeaveBlockResponse> getLeaveBlocksForMonth(Long userId, String yearMonth) {
        Employee emp = employeeRepo.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile not found"));
        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        List<LeaveRequest> leaves = requestRepo.findApprovedByEmployeeAndDateRange(
                emp.getCompanyId(), emp.getId(), from, to);

        List<LeaveBlockResponse> blocks = new ArrayList<>();
        for (LeaveRequest lr : leaves) {
            LocalDate cursor = lr.getStartDate().isBefore(from) ? from : lr.getStartDate();
            LocalDate end    = lr.getEndDate().isAfter(to) ? to : lr.getEndDate();
            while (!cursor.isAfter(end)) {
                blocks.add(new LeaveBlockResponse(cursor, lr.isHalfDay(), lr.getHalfDayPeriod(), lr.getStatus()));
                cursor = cursor.plusDays(1);
            }
        }
        return blocks;
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> listRequests(Long companyId, Long employeeId,
                                                    String status, Pageable pageable) {
        Page<LeaveRequest> page;
        if (employeeId != null) {
            page = requestRepo.findByCompanyIdAndEmployeeIdAndDeletedAtIsNull(companyId, employeeId, pageable);
        } else if (status != null) {
            page = requestRepo.findByCompanyIdAndStatusAndDeletedAtIsNull(
                    companyId, LeaveStatus.valueOf(status), pageable);
        } else {
            page = requestRepo.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);
        }
        return page.map(r -> toResponse(r, companyId));
    }

    @Transactional
    public LeaveRequestResponse createRequest(Long companyId, Long employeeId, CreateLeaveRequest req) {
        Employee emp = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));

        // Chặn nhân sự đang thử việc
        if ("PROBATION".equals(emp.getStatus())) {
            throw new BusinessException(
                    "Nhân sự đang trong thời gian thử việc không được phép xin nghỉ phép.",
                    HttpStatus.FORBIDDEN, "PROBATION_NO_LEAVE");
        }

        LeaveType leaveType = typeRepo.findByIdAndCompanyId(req.leaveTypeId(), companyId)
                .orElseThrow(() -> new NotFoundException("LeaveType", req.leaveTypeId()));

        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessException("End date must be after start date");
        }

        // Overlap check
        long overlapping = requestRepo.countOverlapping(companyId, employeeId, req.startDate(), req.endDate());
        if (overlapping > 0) {
            throw new BusinessException(
                "Khoảng thời gian từ " + req.startDate() + " đến " + req.endDate() +
                " đã có đơn nghỉ phép đang chờ duyệt hoặc đã được duyệt. Vui lòng chọn ngày khác.");
        }

        validateHalfDayPeriod(req.halfDay(), req.halfDayPeriod());
        BigDecimal totalDays = calculateTotalDays(req.startDate(), req.endDate(), req.halfDay());

        // ── Bug 1: Check quota TRƯỚC khi tạo PENDING ──
        if (leaveType.getMaxDays() != null) {
            int year = req.startDate().getYear();
            BigDecimal remaining = getRemainingQuota(companyId, employeeId, req.leaveTypeId(), year, leaveType);
            // Trừ thêm các đơn PENDING đang chờ duyệt (chưa deduct vào balance)
            BigDecimal pendingDays = sumPendingDays(companyId, employeeId, req.leaveTypeId(), year);
            BigDecimal available = remaining.subtract(pendingDays);
            if (available.compareTo(totalDays) < 0) {
                throw new BusinessException(
                    "Không đủ ngày phép. Còn lại: " + available.setScale(1, RoundingMode.HALF_UP) +
                    " ngày (đã tính " + pendingDays.setScale(1, RoundingMode.HALF_UP) +
                    " ngày đang chờ duyệt), yêu cầu: " + totalDays + " ngày.",
                    HttpStatus.BAD_REQUEST, "INSUFFICIENT_QUOTA");
            }
        }

        LeaveRequest lr = LeaveRequest.builder()
                .companyId(companyId).employeeId(employeeId)
                .leaveTypeId(req.leaveTypeId())
                .startDate(req.startDate()).endDate(req.endDate())
                .totalDays(totalDays)
                .halfDay(req.halfDay())
                .halfDayPeriod(req.halfDayPeriod())
                .reason(req.reason())
                .status(LeaveStatus.PENDING)
                .build();
        return toResponse(requestRepo.save(lr), companyId);
    }

    @Transactional
    public LeaveRequestResponse approve(Long id, Long companyId) {
        LeaveRequest lr = requestRepo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("LeaveRequest", id));
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("Only PENDING requests can be approved");
        }

        LeaveType leaveType = typeRepo.findById(lr.getLeaveTypeId())
                .orElseThrow(() -> new NotFoundException("LeaveType", lr.getLeaveTypeId()));

        // ── Bug 2+5: Track TẤT CẢ loại phép có maxDays (bỏ điều kiện isPaid) ──
        if (leaveType.getMaxDays() != null) {
            deductLeaveBalance(companyId, lr.getEmployeeId(), lr.getLeaveTypeId(),
                    lr.getStartDate().getYear(), lr.getTotalDays(), leaveType);
        }

        lr.setStatus(LeaveStatus.APPROVED);
        lr.setApprovedBy(SecurityUtils.getCurrentUserId());
        lr.setApprovedAt(LocalDateTime.now());
        return toResponse(requestRepo.save(lr), companyId);
    }

    @Transactional
    public LeaveRequestResponse reject(Long id, Long companyId, String reason) {
        LeaveRequest lr = requestRepo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("LeaveRequest", id));
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException("Only PENDING requests can be rejected");
        }
        lr.setStatus(LeaveStatus.REJECTED);
        lr.setRejectReason(reason);
        return toResponse(requestRepo.save(lr), companyId);
    }

    @Transactional
    public LeaveRequestResponse cancel(Long id, Long companyId, Long currentUserId) {
        LeaveRequest lr = requestRepo.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("LeaveRequest", id));

        if (currentUserId != null) {
            Long empId = employeeRepo.findByUserIdAndDeletedAtIsNull(currentUserId)
                    .map(Employee::getId).orElse(null);
            if (empId != null && !empId.equals(lr.getEmployeeId())) {
                throw new BusinessException("Không có quyền hủy đơn nghỉ phép của người khác",
                        HttpStatus.FORBIDDEN, "NOT_OWNER");
            }
        }

        if (lr.getStatus() == LeaveStatus.REJECTED) {
            throw new BusinessException("Cannot cancel an already rejected request");
        }

        // ── Bug 2+5: Restore cho TẤT CẢ loại phép có maxDays ──
        if (lr.getStatus() == LeaveStatus.APPROVED) {
            LeaveType leaveType = typeRepo.findById(lr.getLeaveTypeId())
                    .orElseThrow(() -> new NotFoundException("LeaveType", lr.getLeaveTypeId()));
            if (leaveType.getMaxDays() != null) {
                restoreLeaveBalance(companyId, lr.getEmployeeId(), lr.getLeaveTypeId(),
                        lr.getStartDate().getYear(), lr.getTotalDays());
            }
        }

        lr.setStatus(LeaveStatus.CANCELLED);
        return toResponse(requestRepo.save(lr), companyId);
    }

    // ══════════════════════════════════════════════════════════════
    // Balance: Deduct / Restore (Bug 3: pessimistic lock)
    // ══════════════════════════════════════════════════════════════

    private void deductLeaveBalance(Long companyId, Long employeeId, Long leaveTypeId,
                                     int year, BigDecimal days, LeaveType lt) {
        // Đảm bảo balance tồn tại trước khi lock
        if (balanceRepo.findByCompanyIdAndEmployeeIdAndLeaveTypeIdAndYear(
                companyId, employeeId, leaveTypeId, year).isEmpty()) {
            // Tạo mới nếu chưa có — unique constraint bảo vệ duplicate
            try {
                balanceRepo.save(LeaveBalance.builder()
                        .companyId(companyId)
                        .employeeId(employeeId)
                        .leaveTypeId(leaveTypeId)
                        .year(year)
                        .totalQuota(BigDecimal.valueOf(lt.getMaxDays()))
                        .build());
                balanceRepo.flush();
            } catch (Exception e) {
                // Concurrent thread đã tạo → ignore, sẽ lock row bên dưới
                log.debug("Balance already created by concurrent thread, proceeding with lock");
            }
        }

        // Lock row (giờ chắc chắn tồn tại) rồi deduct
        LeaveBalance balance = balanceRepo
                .findForUpdate(companyId, employeeId, leaveTypeId, year)
                .orElseThrow(() -> new BusinessException("Lỗi hệ thống: không tìm thấy leave balance"));

        if (!balance.hasSufficientBalance(days)) {
            throw new BusinessException(
                "Nhân sự không còn đủ ngày phép. Còn lại: " + balance.getRemainingDays() +
                " ngày, yêu cầu: " + days + " ngày.");
        }

        balance.addUsedDays(days);
        balanceRepo.save(balance);
    }

    private void restoreLeaveBalance(Long companyId, Long employeeId, Long leaveTypeId,
                                      int year, BigDecimal days) {
        // Bug 6: Log warning thay vì im lặng
        LeaveBalance balance = balanceRepo
                .findForUpdate(companyId, employeeId, leaveTypeId, year)
                .orElse(null);

        if (balance == null) {
            log.error("Cannot restore leave balance — record not found! company={}, employee={}, leaveType={}, year={}, days={}",
                    companyId, employeeId, leaveTypeId, year, days);
            return;
        }

        balance.subtractUsedDays(days);
        balanceRepo.save(balance);
        log.info("Restored {} leave days for employee={}, leaveType={}, year={}",
                days, employeeId, leaveTypeId, year);
    }

    // ══════════════════════════════════════════════════════════════
    // Bug 1 helpers: Quota check trước khi submit
    // ══════════════════════════════════════════════════════════════

    private BigDecimal getRemainingQuota(Long companyId, Long employeeId, Long leaveTypeId,
                                          int year, LeaveType lt) {
        return balanceRepo.findByCompanyIdAndEmployeeIdAndLeaveTypeIdAndYear(
                        companyId, employeeId, leaveTypeId, year)
                .map(LeaveBalance::getRemainingDays)
                .orElseGet(() -> {
                    // Chưa có balance → remaining = maxDays (chưa dùng gì)
                    return lt.getMaxDays() != null
                            ? BigDecimal.valueOf(lt.getMaxDays())
                            : BigDecimal.ZERO;
                });
    }

    /** Tổng ngày phép đang PENDING (chưa deduct vào balance) cho cùng loại phép + năm */
    private BigDecimal sumPendingDays(Long companyId, Long employeeId, Long leaveTypeId, int year) {
        List<LeaveRequest> pending = requestRepo.findByEmployeeIdAndStatusAndDeletedAtIsNull(
                employeeId, LeaveStatus.PENDING);
        return pending.stream()
                .filter(lr -> lr.getLeaveTypeId().equals(leaveTypeId)
                        && lr.getStartDate().getYear() == year
                        && lr.getCompanyId().equals(companyId))
                .map(LeaveRequest::getTotalDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ══════════════════════════════════════════════════════════════
    // Auto-grant quota khi tạo nhân sự INTERNAL (Bug 4 + Feature)
    // ══════════════════════════════════════════════════════════════

    /**
     * Tự động cấp quota phép cho nhân sự mới theo tỷ lệ tháng còn lại trong năm.
     * Chỉ cấp cho loại phép có autoGrantOnCreate = true + maxDays != null.
     *
     * Công thức: quota = (12 - tháng_vào) / 12 × maxDays, làm tròn theo grantRounding
     */
    @Transactional
    public void autoGrantQuotaForNewEmployee(Long companyId, Long employeeId, LocalDate startDate) {
        if (startDate == null) {
            log.warn("Cannot auto-grant quota: startDate is null for employee={}", employeeId);
            return;
        }

        int year = startDate.getYear();
        int startMonth = startDate.getMonthValue();
        // Tháng 1 → 12 tháng còn lại (full), tháng 12 → 1 tháng còn lại
        int monthsRemaining = 13 - startMonth;

        List<LeaveType> autoGrantTypes = typeRepo.findByCompanyIdAndActiveTrue(companyId).stream()
                .filter(lt -> lt.isAutoGrantOnCreate() && lt.getMaxDays() != null)
                .toList();

        for (LeaveType lt : autoGrantTypes) {
            // Kiểm tra đã có balance chưa (tránh duplicate)
            if (balanceRepo.findByCompanyIdAndEmployeeIdAndLeaveTypeIdAndYear(
                    companyId, employeeId, lt.getId(), year).isPresent()) {
                continue;
            }

            BigDecimal maxDays = BigDecimal.valueOf(lt.getMaxDays());
            BigDecimal ratio = BigDecimal.valueOf(monthsRemaining)
                    .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            BigDecimal quota = maxDays.multiply(ratio);

            // Làm tròn theo cấu hình
            RoundingMode rounding = switch (lt.getGrantRounding()) {
                case "FLOOR" -> RoundingMode.FLOOR;
                case "CEIL", "CEILING" -> RoundingMode.CEILING;
                default -> RoundingMode.HALF_UP;
            };
            quota = quota.setScale(1, rounding);

            if (quota.compareTo(BigDecimal.ZERO) <= 0) continue;

            LeaveBalance balance = LeaveBalance.builder()
                    .companyId(companyId)
                    .employeeId(employeeId)
                    .leaveTypeId(lt.getId())
                    .year(year)
                    .totalQuota(quota)
                    .build();
            balanceRepo.save(balance);

            log.info("Auto-granted {} days of '{}' for employee={}, year={} (startMonth={}, rounding={})",
                    quota, lt.getName(), employeeId, year, startMonth, lt.getGrantRounding());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Leave Balances (admin)
    // ══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getLeaveBalances(Long companyId, int year) {
        List<Employee> employees = employeeRepo.findByCompanyIdAndDeletedAtIsNull(companyId);
        List<Long> empIds = employees.stream().map(Employee::getId).toList();
        List<LeaveBalance> balances = balanceRepo.findByCompanyIdAndYearAndEmployeeIdIn(companyId, year, empIds);
        Map<Long, Employee> empMap = employees.stream()
                .collect(java.util.stream.Collectors.toMap(Employee::getId, e -> e));

        return balances.stream().map(b -> {
            Employee emp = empMap.get(b.getEmployeeId());
            LeaveType lt = typeRepo.findById(b.getLeaveTypeId()).orElse(null);
            return new LeaveBalanceResponse(
                    b.getId(), b.getEmployeeId(),
                    emp != null ? emp.getFullName() : null,
                    emp != null ? emp.getEmployeeCode() : null,
                    b.getLeaveTypeId(),
                    lt != null ? lt.getName() : null,
                    lt != null ? lt.getCode() : null,
                    lt != null && lt.isPaid(),
                    b.getYear(),
                    b.getTotalQuota(), b.getUsedDays(), b.getCarriedOver(),
                    b.getRemainingDays());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getEmployeeLeaveBalances(Long companyId, Long employeeId, int year) {
        List<LeaveBalance> balances = balanceRepo.findByCompanyIdAndEmployeeIdAndYear(companyId, employeeId, year);
        Employee emp = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));

        return balances.stream().map(b -> {
            LeaveType lt = typeRepo.findById(b.getLeaveTypeId()).orElse(null);
            return new LeaveBalanceResponse(
                    b.getId(), b.getEmployeeId(),
                    emp.getFullName(), emp.getEmployeeCode(),
                    b.getLeaveTypeId(),
                    lt != null ? lt.getName() : null,
                    lt != null ? lt.getCode() : null,
                    lt != null && lt.isPaid(),
                    b.getYear(),
                    b.getTotalQuota(), b.getUsedDays(), b.getCarriedOver(),
                    b.getRemainingDays());
        }).toList();
    }

    @Transactional
    public LeaveBalanceResponse grantLeaveBalance(Long companyId, Long employeeId,
                                                   Long leaveTypeId, int year,
                                                   BigDecimal totalQuota) {
        employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));
        typeRepo.findByIdAndCompanyId(leaveTypeId, companyId)
                .orElseThrow(() -> new NotFoundException("LeaveType", leaveTypeId));

        LeaveBalance balance = balanceRepo
                .findByCompanyIdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employeeId, leaveTypeId, year)
                .orElse(LeaveBalance.builder()
                        .companyId(companyId).employeeId(employeeId)
                        .leaveTypeId(leaveTypeId).year(year).build());

        balance.setTotalQuota(totalQuota);
        balance = balanceRepo.save(balance);

        Employee emp = employeeRepo.findById(employeeId).orElseThrow();
        LeaveType lt = typeRepo.findById(leaveTypeId).orElseThrow();
        return new LeaveBalanceResponse(
                balance.getId(), balance.getEmployeeId(),
                emp.getFullName(), emp.getEmployeeCode(),
                balance.getLeaveTypeId(), lt.getName(), lt.getCode(), lt.isPaid(),
                balance.getYear(), balance.getTotalQuota(), balance.getUsedDays(),
                balance.getCarriedOver(), balance.getRemainingDays());
    }

    // ══════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════

    private LeaveRequestResponse toResponse(LeaveRequest lr, Long companyId) {
        String empName = employeeRepo.findByIdAndCompanyIdAndDeletedAtIsNull(lr.getEmployeeId(), companyId)
                .map(Employee::getFullName).orElse("Unknown");
        String typeName = typeRepo.findById(lr.getLeaveTypeId())
                .map(LeaveType::getName).orElse("Unknown");
        return new LeaveRequestResponse(
                lr.getId(), lr.getCompanyId(), lr.getEmployeeId(), empName,
                lr.getLeaveTypeId(), typeName, lr.getStartDate(), lr.getEndDate(),
                lr.getTotalDays(), lr.isHalfDay(), lr.getHalfDayPeriod(), lr.getReason(),
                lr.getStatus(), lr.getApprovedBy(), lr.getApprovedAt(), lr.getRejectReason(),
                lr.getCreatedAt()
        );
    }
}
