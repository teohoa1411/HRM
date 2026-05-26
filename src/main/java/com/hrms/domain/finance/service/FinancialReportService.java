package com.hrms.domain.finance.service;

import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.customer.repository.CustomerRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.finance.dto.EmployeeFinancialDto;
import com.hrms.domain.finance.dto.MonthlySummaryDto;
import com.hrms.domain.finance.entity.MonthlyFinancialRecord;
import com.hrms.domain.finance.repository.MonthlyFinancialRecordRepository;
import com.hrms.domain.payroll.entity.PayrollRecord;
import com.hrms.domain.payroll.repository.PayrollRecordRepository;
import com.hrms.domain.statement.entity.Statement;
import com.hrms.domain.statement.entity.StatementLineItem;
import com.hrms.domain.statement.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final MonthlyFinancialRecordRepository financialRecordRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final StatementRepository statementRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    /**
     * Sinh MonthlyFinancialRecord tổng hợp sau khi kế toán confirm toàn bộ.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateMonthlyFinancialRecords(Long companyId, String yearMonth) {
        List<BillingRecord> billings = billingRecordRepository
                .findByCompanyIdAndBillingMonth(companyId, yearMonth);

        Map<Long, PayrollRecord> payrollMap = payrollRecordRepository
                .findByCompanyIdAndPayrollMonth(companyId, yearMonth)
                .stream()
                .collect(Collectors.toMap(PayrollRecord::getEmployeeId, Function.identity()));

        // Aggregate billing records theo (employeeId, customerId) — sau V29 mỗi cặp này
        // có thể bao gồm nhiều period (vd: rời rồi quay lại customer cùng tháng).
        // Mỗi cặp → 1 MonthlyFinancialRecord (schema đã unique theo customer_id).
        Map<String, List<BillingRecord>> grouped = billings.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getEmployeeId() + ":" + (b.getCustomerId() == null ? "0" : b.getCustomerId())));

        // Existing records (để update thay vì duplicate)
        Map<String, MonthlyFinancialRecord> existingByKey = financialRecordRepository
                .findByCompanyIdAndRecordMonth(companyId, yearMonth).stream()
                .collect(Collectors.toMap(
                        r -> r.getEmployeeId() + ":" + (r.getCustomerId() == null ? "0" : r.getCustomerId()),
                        Function.identity(),
                        (a, b) -> a));

        List<MonthlyFinancialRecord> records = new ArrayList<>();

        for (Map.Entry<String, List<BillingRecord>> entry : grouped.entrySet()) {
            List<BillingRecord> group = entry.getValue();
            BillingRecord first = group.get(0);
            Long empId = first.getEmployeeId();
            Long customerId = first.getCustomerId();

            // Sum across periods cùng (emp, customer)
            BigDecimal totalSell = group.stream().map(BillingRecord::getSellAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = group.stream().map(BillingRecord::getCostAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDays = group.stream().map(BillingRecord::getDaysWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal cost = totalCost;
            BigDecimal bonus = BigDecimal.ZERO;
            BigDecimal deduction = BigDecimal.ZERO;

            if (first.getSourceType() == EmployeeSourceType.INTERNAL) {
                // Payroll là per-employee-per-month → bonus/deduction áp 1 lần
                // (nếu NV có nhiều customer trong tháng, chỉ áp vào customer "chính" — first encountered)
                PayrollRecord pr = payrollMap.get(empId);
                if (pr != null) {
                    cost = pr.getNetSalary().multiply(
                            totalCost.compareTo(BigDecimal.ZERO) > 0
                                    ? totalCost.divide(pr.getCalculatedSalary().max(BigDecimal.ONE), 6, RoundingMode.HALF_UP)
                                    : BigDecimal.ONE);
                    bonus = pr.getBonus();
                    deduction = pr.getDeduction();
                }
            }

            BigDecimal revenue = totalSell;
            BigDecimal billedDays = totalDays;
            BigDecimal profit = revenue.subtract(cost);
            BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                    ? profit.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            MonthlyFinancialRecord existing = existingByKey.get(entry.getKey());
            if (existing != null) {
                updateRecord(existing, first, revenue, billedDays, cost, bonus, deduction, profit, margin, customerId);
                records.add(existing);
            } else {
                records.add(MonthlyFinancialRecord.builder()
                        .companyId(companyId)
                        .employeeId(empId)
                        .recordMonth(yearMonth)
                        .customerId(customerId)
                        .sourceType(first.getSourceType())
                        .vendorId(first.getVendorId())
                        .billedDays(billedDays)
                        .unitSellPrice(first.getUnitSellPrice())
                        .revenueAmount(revenue)
                        .workedDays(totalDays)
                        .unitCostPrice(first.getUnitCostPrice())
                        .baseCostAmount(totalCost)
                        .bonusAmount(bonus)
                        .deductionAmount(deduction)
                        .netCostAmount(cost)
                        .profitAmount(profit)
                        .profitMargin(margin)
                        .status("DRAFT")
                        .build());
            }
        }

        financialRecordRepository.saveAll(records);
        log.info("Generated {} financial records for company {} month {}", records.size(), companyId, yearMonth);
    }

    /** Tổng hợp tháng */
    @Transactional(readOnly = true)
    public MonthlySummaryDto getMonthlySummary(Long companyId, String yearMonth) {
        List<MonthlyFinancialRecord> records = financialRecordRepository
                .findByCompanyIdAndRecordMonth(companyId, yearMonth);

        BigDecimal revenue = sum(records, MonthlyFinancialRecord::getRevenueAmount);
        BigDecimal payroll = records.stream()
                .filter(r -> r.getSourceType() == EmployeeSourceType.INTERNAL)
                .map(MonthlyFinancialRecord::getNetCostAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vendor = records.stream()
                .filter(r -> r.getSourceType() == EmployeeSourceType.VENDOR)
                .map(MonthlyFinancialRecord::getNetCostAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = revenue.subtract(payroll).subtract(vendor);
        BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? profit.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        LocalDateTime lastCalc = records.stream()
                .map(MonthlyFinancialRecord::getUpdatedAt)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new MonthlySummaryDto(yearMonth, revenue, payroll, vendor, profit, margin, records.size(), lastCalc);
    }

    /** Báo cáo per-employee */
    @Transactional(readOnly = true)
    public List<EmployeeFinancialDto> getPerEmployeeReport(Long companyId, String yearMonth) {
        List<MonthlyFinancialRecord> records = financialRecordRepository
                .findByCompanyIdAndRecordMonth(companyId, yearMonth);

        Map<Long, Employee> empMap = employeeRepository
                .findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        // Enrich customer names in batch
        List<Long> customerIds = records.stream()
                .map(MonthlyFinancialRecord::getCustomerId)
                .filter(id -> id != null)
                .distinct().toList();
        Map<Long, String> customerNames = customerRepository.findAllById(customerIds)
                .stream().collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        return records.stream().map(r -> {
            Employee emp = empMap.get(r.getEmployeeId());
            return new EmployeeFinancialDto(
                    r.getEmployeeId(),
                    emp != null ? emp.getEmployeeCode() : null,
                    emp != null ? emp.getFullName() : null,
                    r.getSourceType().name(),
                    r.getCustomerId() != null ? customerNames.get(r.getCustomerId()) : null,
                    r.getBilledDays(),
                    r.getRevenueAmount(),
                    r.getNetCostAmount(),
                    r.getProfitAmount(),
                    r.getProfitMargin()
            );
        }).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<Long, BigDecimal> buildRevenueMap(Long companyId, String yearMonth) {
        return statementRepository
                .findByCompanyIdAndStatementMonthAndStatementType(companyId, yearMonth, "CUSTOMER")
                .stream()
                .flatMap(s -> s.getLineItems().stream())
                .collect(Collectors.toMap(
                        StatementLineItem::getEmployeeId,
                        StatementLineItem::getAdjustedAmount,
                        BigDecimal::add
                ));
    }

    private Map<Long, BigDecimal> buildBilledDaysMap(Long companyId, String yearMonth) {
        return statementRepository
                .findByCompanyIdAndStatementMonthAndStatementType(companyId, yearMonth, "CUSTOMER")
                .stream()
                .flatMap(s -> s.getLineItems().stream())
                .collect(Collectors.toMap(
                        StatementLineItem::getEmployeeId,
                        StatementLineItem::getAdjustedDays,
                        BigDecimal::add
                ));
    }

    private Map<Long, Long> buildCustomerMap(Long companyId, String yearMonth) {
        Map<Long, Long> result = new java.util.HashMap<>();
        statementRepository
                .findByCompanyIdAndStatementMonthAndStatementType(companyId, yearMonth, "CUSTOMER")
                .forEach(s -> s.getLineItems()
                        .forEach(li -> result.put(li.getEmployeeId(), s.getRecipientId())));
        return result;
    }

    private void updateRecord(MonthlyFinancialRecord r, BillingRecord b,
                               BigDecimal revenue, BigDecimal billedDays,
                               BigDecimal cost, BigDecimal bonus, BigDecimal deduction,
                               BigDecimal profit, BigDecimal margin, Long customerId) {
        r.setCustomerId(customerId);
        r.setBilledDays(billedDays);
        r.setUnitSellPrice(b.getUnitSellPrice());
        r.setRevenueAmount(revenue);
        r.setWorkedDays(b.getDaysWorked());
        r.setUnitCostPrice(b.getUnitCostPrice());
        r.setBaseCostAmount(b.getCostAmount());
        r.setBonusAmount(bonus);
        r.setDeductionAmount(deduction);
        r.setNetCostAmount(cost);
        r.setProfitAmount(profit);
        r.setProfitMargin(margin);
    }

    private BigDecimal sum(List<MonthlyFinancialRecord> records,
                            java.util.function.Function<MonthlyFinancialRecord, BigDecimal> getter) {
        return records.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
