package com.hrms.web.controller;

import com.hrms.domain.billing.dto.BillingRecordResponse;
import com.hrms.domain.billing.entity.BillingRecord;
import com.hrms.domain.billing.repository.BillingRecordRepository;
import com.hrms.domain.billing.service.CostCalculationService;
import com.hrms.domain.customer.repository.CustomerRepository;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.finance.service.FinancialReportService;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.domain.vendor.repository.VendorRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Monthly cost calculation & billing records")
public class BillingController {

    private final CostCalculationService calculationService;
    private final FinancialReportService financialReportService;
    private final BillingRecordRepository billingRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final JobRoleRepository jobRoleRepository;
    private final VendorRepository vendorRepository;
    private final CustomerRepository customerRepository;

    @PostMapping("/calculate/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Trigger cost calculation for a month (YYYY-MM)")
    public ResponseEntity<ApiResponse<String>> calculate(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        int count = calculationService.calculateForMonth(companyId, yearMonth);
        // Tá»± Ä‘á»™ng cáº­p nháº­t bÃ¡o cÃ¡o tÃ i chÃ­nh sau khi tÃ­nh billing
        try {
            financialReportService.generateMonthlyFinancialRecords(companyId, yearMonth);
        } catch (Exception e) {
            log.warn("Auto-generate financial records after billing failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok("ÄÃ£ tÃ­nh chi phÃ­ cho " + count + " nhÃ¢n viÃªn thÃ¡ng " + yearMonth));
    }

    @GetMapping("/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get billing records for a month")
    public ResponseEntity<ApiResponse<List<BillingRecordResponse>>> getByMonth(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        List<BillingRecord> records = billingRecordRepository.findByCompanyIdAndBillingMonth(companyId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(enrich(records)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get billing records in date range (fromMonth/toMonth YYYY-MM)")
    public ResponseEntity<ApiResponse<List<BillingRecordResponse>>> getByRange(
            @RequestParam String fromMonth,
            @RequestParam String toMonth,
            @RequestParam(required = false) Long jobRoleId,
            @RequestParam(required = false) String sourceType) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        List<BillingRecord> records = billingRecordRepository.findFiltered(companyId, fromMonth, toMonth, jobRoleId, sourceType);
        return ResponseEntity.ok(ApiResponse.ok(enrich(records)));
    }

    /** Enrich billing records with display names. */
    private List<BillingRecordResponse> enrich(List<BillingRecord> records) {
        if (records.isEmpty()) return List.of();

        // Build lookup maps (batch fetch to avoid N+1)
        Map<Long, String> empNames = employeeRepository.findAllById(
                records.stream().map(BillingRecord::getEmployeeId).distinct().toList()
        ).stream().collect(Collectors.toMap(e -> e.getId(), e -> e.getFullName()));

        Map<Long, String> empCodes = employeeRepository.findAllById(
                records.stream().map(BillingRecord::getEmployeeId).distinct().toList()
        ).stream().collect(Collectors.toMap(e -> e.getId(), e -> e.getEmployeeCode()));

        Map<Long, String> jrNames = jobRoleRepository.findAllById(
                records.stream().map(BillingRecord::getJobRoleId).distinct().toList()
        ).stream().collect(Collectors.toMap(e -> e.getId(), e -> e.getName()));

        Map<Long, String> vendorNames = records.stream()
                .map(BillingRecord::getVendorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> vendorRepository.findById(id).map(v -> v.getName()).orElse("â€”")
                ));

        Map<Long, String> customerNames = records.stream()
                .map(BillingRecord::getCustomerId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> customerRepository.findById(id).map(c -> c.getName()).orElse("â€”")
                ));

        return records.stream().map(b -> new BillingRecordResponse(
                b.getId(),
                b.getCompanyId(),
                b.getEmployeeId(),
                empNames.getOrDefault(b.getEmployeeId(), "â€”"),
                empCodes.getOrDefault(b.getEmployeeId(), "â€”"),
                b.getJobRoleId(),
                jrNames.getOrDefault(b.getJobRoleId(), "â€”"),
                b.getBillingMonth(),
                b.getPeriodStart(),
                b.getPeriodEnd(),
                b.getSourceType(),
                b.getVendorId(),
                b.getVendorId() != null ? vendorNames.getOrDefault(b.getVendorId(), "â€”") : null,
                b.getCustomerId(),
                b.getCustomerId() != null ? customerNames.getOrDefault(b.getCustomerId(), "â€”") : null,
                b.getDaysWorked(),
                b.getDaysOff(),
                b.getTotalWorkdays(),
                b.getPriceUnit(),
                b.getUnitCostPrice(),
                b.getUnitSellPrice(),
                b.getCostAmount(),
                b.getSellAmount(),
                b.getCurrency(),
                b.getCalcStatus()
        )).toList();
    }
}
