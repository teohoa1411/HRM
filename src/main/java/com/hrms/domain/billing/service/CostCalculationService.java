package com.hrms.domain.billing.service;

import com.hrms.domain.assignment.entity.EmployeeAssignment;
import com.hrms.domain.assignment.repository.EmployeeAssignmentRepository;
import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.calendar.service.WorkCalendarService;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.PriceUnit;
import com.hrms.domain.customer.repository.CustomerPriceRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.leave.repository.LeaveRequestRepository;
import com.hrms.domain.salary.repository.InternalSalaryRepository;
import com.hrms.domain.timesheet.entity.Timesheet;
import com.hrms.domain.timesheet.repository.TimesheetRepository;
import com.hrms.domain.vendor.repository.VendorPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tính chi phí hàng tháng cho từng nhân sự, hỗ trợ <b>period-based billing</b>:
 * một nhân sự trong 1 tháng có thể tạo ra nhiều {@link BillingRecord} nếu chuyển dự án
 * giữa tháng hoặc có gap không phân công.
 *
 * <h3>Ràng buộc:</h3>
 * <ul>
 *   <li>1 NV / 1 ngày = tối đa 1 customer (enforce ở
 *       {@link com.hrms.domain.assignment.service.AssignmentService#assertNoOverlap}).</li>
 *   <li>Mỗi BillingRecord có {@code period_start}/{@code period_end} ⊂ [monthStart, monthEnd].</li>
 *   <li>Period gap (customer_id=null): vẫn tạo record để track chi phí nội bộ
 *       (NV INTERNAL ăn lương trong gap; sell = 0).</li>
 * </ul>
 *
 * <h3>Công thức cost/sell cho mỗi period:</h3>
 * <ul>
 *   <li><b>DAILY:</b> {@code amount = daysEffectiveInPeriod × unitPrice}</li>
 *   <li><b>MONTHLY:</b> {@code amount = unitPrice × (workdaysInPeriod / totalWorkdaysOfMonth)
 *                                       − (unpaidLeaveInPeriod × unitPrice / totalWorkdaysOfMonth)}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostCalculationService {

    private final EmployeeRepository employeeRepository;
    private final TimesheetRepository timesheetRepository;
    private final InternalSalaryRepository salaryRepository;
    private final VendorPriceRepository vendorPriceRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final EmployeeAssignmentRepository assignmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WorkCalendarService workCalendarService;
    private final BillingRecordRepository billingRecordRepository;

    /**
     * Chạy tự động vào ngày 3 mỗi tháng lúc 02:00 để tính chi phí tháng trước.
     */
    @Scheduled(cron = "0 0 2 3 * *")
    public void scheduledCalculation() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String yearMonth = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("Scheduled cost calculation for {}", yearMonth);
        // Calculate for all companies — in a real system, iterate companies
        // Here we trigger manually via API as well
    }

    /**
     * Tính chi phí cho một company trong một tháng.
     * Có thể gọi từ API (manual trigger) hoặc scheduled job.
     */
    @Transactional
    public int calculateForMonth(Long companyId, String yearMonth) {
        log.info("Calculating costs for company={} month={}", companyId, yearMonth);

        YearMonth ym = YearMonth.parse(yearMonth);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        double totalWorkdays = workCalendarService.getTotalWorkdays(companyId, yearMonth);

        List<Employee> employees = employeeRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        int count = 0;
        int failed = 0;
        RuntimeException firstFailure = null;

        for (Employee emp : employees) {
            try {
                processEmployee(emp, companyId, yearMonth, monthStart, monthEnd, totalWorkdays);
                count++;
            } catch (RuntimeException e) {
                failed++;
                // Log đầy đủ stack trace để debug (trước đây chỉ log message → ẩn schema/NPE bugs).
                log.error("Failed to calculate cost for employee={} ({}) month={}: {}",
                        emp.getId(), emp.getFullName(), yearMonth, e.toString(), e);
                if (firstFailure == null) firstFailure = e;
            }
        }

        log.info("Calculated costs for company={} month={}: ok={} failed={}",
                companyId, yearMonth, count, failed);

        // Nếu TOÀN BỘ employee đều fail → schema / config bug, không phải data per-employee → re-throw.
        // Trước đây silent catch khiến user thấy "thành công" nhưng thực tế không có record nào được tạo.
        if (failed > 0 && count == 0 && firstFailure != null) {
            throw new IllegalStateException(
                    "Tính chi phí thất bại cho TẤT CẢ " + failed + " nhân sự (tháng " + yearMonth
                            + "). Có thể do lỗi schema/migration. Lỗi đầu tiên: " + firstFailure.getMessage(),
                    firstFailure);
        }
        return count;
    }

    private void processEmployee(Employee emp, Long companyId, String yearMonth,
                                  LocalDate monthStart, LocalDate monthEnd, double totalWorkdays) {
        // ─── 1. Lấy timesheet 1 lần cho cả tháng, group theo ngày ─────────────────
        Long userId = emp.getUserId();
        Set<LocalDate> workedDates = new HashSet<>();
        if (userId != null) {
            List<Timesheet> entries = timesheetRepository.findByUserAndMonth(userId, companyId, monthStart, monthEnd);
            for (Timesheet t : entries) {
                if (t.getHours() != null && t.getHours().compareTo(BigDecimal.ZERO) > 0) {
                    workedDates.add(t.getWorkDate());
                }
            }
        }

        // ─── 2. Tách tháng thành các period theo assignment ──────────────────────
        List<EmployeeAssignment> assignments =
                assignmentRepository.findOverlapping(emp.getId(), monthStart, monthEnd);
        List<AssignmentPeriodSplitter.Period> periods =
                AssignmentPeriodSplitter.split(assignments, monthStart, monthEnd);

        // ─── 3. Xoá billing cũ của tháng này (sẽ regenerate) ─────────────────────
        // Dùng @Modifying DELETE query để flush ngay xuống DB trước khi INSERT mới,
        // tránh duplicate key do JPA buffer chưa commit khi đã INSERT.
        billingRecordRepository.deleteByCompanyAndEmployeeAndMonth(companyId, emp.getId(), yearMonth);
        billingRecordRepository.flush();

        // ─── 4. Loop từng period → tạo BillingRecord ─────────────────────────────
        for (AssignmentPeriodSplitter.Period p : periods) {
            createBillingRecordForPeriod(emp, companyId, yearMonth, p, workedDates,
                    monthStart, monthEnd, totalWorkdays);
        }
    }

    private void createBillingRecordForPeriod(Employee emp, Long companyId, String yearMonth,
                                              AssignmentPeriodSplitter.Period p,
                                              Set<LocalDate> workedDates,
                                              LocalDate monthStart, LocalDate monthEnd,
                                              double totalWorkdaysOfMonth) {
        // ─── Đếm ngày công thực tế trong period ──────────────────────────────────
        long daysWorkedInPeriod = workedDates.stream()
                .filter(d -> !d.isBefore(p.getStart()) && !d.isAfter(p.getEnd()))
                .count();
        BigDecimal daysWorked = BigDecimal.valueOf(daysWorkedInPeriod);

        // ─── Phép trong period ───────────────────────────────────────────────────
        BigDecimal paidLeaveDays = leaveRequestRepository
                .sumPaidApprovedDaysInMonth(emp.getId(), p.getStart(), p.getEnd());
        BigDecimal unpaidLeaveDays = leaveRequestRepository
                .sumUnpaidApprovedDaysInMonth(emp.getId(), p.getStart(), p.getEnd());
        BigDecimal daysOff = paidLeaveDays.add(unpaidLeaveDays);

        // ─── Lookup giá tại period.start (giá hợp đồng có thể đổi giữa tháng) ────
        LocalDate refDate = p.getStart();
        BigDecimal unitCostPrice = BigDecimal.ZERO;
        BigDecimal unitSellPrice = BigDecimal.ZERO;
        // costPriceUnit và sellPriceUnit tách biệt: INTERNAL luôn MONTHLY cho cost,
        // customer có thể dùng DAILY cho sell mà không ảnh hưởng đến cost.
        PriceUnit costPriceUnit = PriceUnit.MONTHLY;
        PriceUnit sellPriceUnit = PriceUnit.MONTHLY;
        Long vendorId  = emp.getVendorId();
        Long customerId = p.getCustomerId(); // null nếu là gap

        if (emp.getSourceType() == EmployeeSourceType.VENDOR && vendorId != null) {
            var vp = vendorPriceRepository.findPriceAt(vendorId, emp.getJobRoleId(), refDate);
            if (vp.isPresent()) {
                unitCostPrice = vp.get().getPrice();
                costPriceUnit = vp.get().getPriceUnit();
            }
        } else if (emp.getSourceType() == EmployeeSourceType.INTERNAL) {
            var salary = salaryRepository.findSalaryAt(emp.getId(), refDate);
            if (salary.isPresent()) {
                unitCostPrice = salary.get().getMonthlySalary();
                costPriceUnit = PriceUnit.MONTHLY; // lương tháng luôn MONTHLY
            }
        }

        if (customerId != null) {
            var cp = customerPriceRepository.findPriceAt(customerId, emp.getJobRoleId(), refDate);
            if (cp.isPresent()) {
                unitSellPrice = cp.get().getPrice();
                sellPriceUnit = cp.get().getPriceUnit(); // priceUnit của customer chỉ dùng cho sell
            }
        }

        // ─── Tính amount ─────────────────────────────────────────────────────────
        // Cost: NV INTERNAL ăn lương kể cả khi period là gap (chưa được phân công)
        BigDecimal costAmount;
        if (emp.getSourceType() == EmployeeSourceType.INTERNAL) {
            costAmount = calcInternalAmount(unitCostPrice, costPriceUnit, daysWorked, paidLeaveDays,
                    unpaidLeaveDays, totalWorkdaysOfMonth, p.getStart(), p.getEnd(), monthStart, monthEnd);
        } else {
            costAmount = calcExternalAmount(unitCostPrice, costPriceUnit, daysWorked, unpaidLeaveDays,
                    totalWorkdaysOfMonth, p.getStart(), p.getEnd(), monthStart, monthEnd);
        }

        // Sell: chỉ có nếu period có customer, không bao gồm phép
        BigDecimal sellAmount = (customerId == null) ? BigDecimal.ZERO
                : calcExternalAmount(unitSellPrice, sellPriceUnit, daysWorked, unpaidLeaveDays,
                        totalWorkdaysOfMonth, p.getStart(), p.getEnd(), monthStart, monthEnd);

        // ─── Insert (đã xoá record cũ ở processEmployee) ─────────────────────────
        BillingRecord record = BillingRecord.builder()
                .companyId(companyId)
                .employeeId(emp.getId())
                .jobRoleId(emp.getJobRoleId())
                .billingMonth(yearMonth)
                .sourceType(emp.getSourceType())
                .periodStart(p.getStart())
                .periodEnd(p.getEnd())
                .vendorId(vendorId)
                .customerId(customerId)
                .daysWorked(daysWorked)
                .daysOff(daysOff)
                .paidLeaveDays(paidLeaveDays)
                .unpaidLeaveDays(unpaidLeaveDays)
                .totalWorkdays(totalWorkdaysOfMonth)
                .priceUnit(costPriceUnit)
                .unitCostPrice(unitCostPrice)
                .unitSellPrice(unitSellPrice)
                .costAmount(costAmount)
                .sellAmount(sellAmount)
                .calcStatus("CALCULATED")
                .build();

        billingRecordRepository.save(record);
    }

    /**
     * Số workday (ngày làm việc theo lịch công ty) trong khoảng [start, end].
     */
    private double countWorkdaysInRange(Long companyId, LocalDate start, LocalDate end) {
        long n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (workCalendarService.isWorkday(companyId, d)) n++;
        }
        return n;
    }

    /**
     * Tính amount cho NV INTERNAL theo period.
     * <ul>
     *   <li>DAILY: {@code (daysWorked + paidLeave) × unitPrice}</li>
     *   <li>MONTHLY: prorate theo tỉ lệ workdaysInPeriod / totalWorkdaysOfMonth, trừ unpaid leave</li>
     * </ul>
     */
    private BigDecimal calcInternalAmount(BigDecimal unitPrice, PriceUnit priceUnit,
                                           BigDecimal daysWorked, BigDecimal paidLeaveDays,
                                           BigDecimal unpaidLeaveDays, double totalWorkdaysOfMonth,
                                           LocalDate periodStart, LocalDate periodEnd,
                                           LocalDate monthStart, LocalDate monthEnd) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        if (priceUnit == PriceUnit.DAILY) {
            BigDecimal effectiveDays = daysWorked.add(paidLeaveDays);
            return unitPrice.multiply(effectiveDays).setScale(2, RoundingMode.HALF_UP);
        }

        // MONTHLY: prorate theo period
        if (totalWorkdaysOfMonth == 0) return unitPrice;
        BigDecimal totalWd = BigDecimal.valueOf(totalWorkdaysOfMonth);

        // Tỉ lệ period trong tháng theo số ngày dương lịch (đơn giản, ổn định)
        double periodDays = (double) (periodEnd.toEpochDay() - periodStart.toEpochDay() + 1);
        double monthDays  = (double) (monthEnd.toEpochDay() - monthStart.toEpochDay() + 1);
        BigDecimal ratio = BigDecimal.valueOf(periodDays / monthDays)
                .setScale(6, RoundingMode.HALF_UP);

        BigDecimal proratedSalary = unitPrice.multiply(ratio);
        BigDecimal deduction = unpaidLeaveDays
                .multiply(unitPrice)
                .divide(totalWd, 2, RoundingMode.HALF_UP);
        return proratedSalary.subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính amount cho khách hàng/vendor theo period (không tính phép có lương).
     */
    private BigDecimal calcExternalAmount(BigDecimal unitPrice, PriceUnit priceUnit,
                                           BigDecimal daysWorked, BigDecimal unpaidLeaveDays,
                                           double totalWorkdaysOfMonth,
                                           LocalDate periodStart, LocalDate periodEnd,
                                           LocalDate monthStart, LocalDate monthEnd) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        if (priceUnit == PriceUnit.DAILY) {
            return unitPrice.multiply(daysWorked).setScale(2, RoundingMode.HALF_UP);
        }

        // MONTHLY: prorate theo period
        if (totalWorkdaysOfMonth == 0) return unitPrice;
        BigDecimal totalWd = BigDecimal.valueOf(totalWorkdaysOfMonth);

        double periodDays = (double) (periodEnd.toEpochDay() - periodStart.toEpochDay() + 1);
        double monthDays  = (double) (monthEnd.toEpochDay() - monthStart.toEpochDay() + 1);
        BigDecimal ratio = BigDecimal.valueOf(periodDays / monthDays)
                .setScale(6, RoundingMode.HALF_UP);

        BigDecimal proratedPrice = unitPrice.multiply(ratio);
        BigDecimal deduction = unpaidLeaveDays
                .multiply(unitPrice)
                .divide(totalWd, 2, RoundingMode.HALF_UP);
        return proratedPrice.subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
