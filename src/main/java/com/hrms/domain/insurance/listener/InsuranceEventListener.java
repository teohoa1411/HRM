package com.hrms.domain.insurance.listener;

import com.hrms.domain.common.event.ContractCreatedEvent;
import com.hrms.domain.common.event.EmployeeTerminatedEvent;
import com.hrms.domain.insurance.service.InsuranceDeclarationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lắng nghe các sự kiện từ Contract & Employee module
 * để tự động tạo báo tăng/giảm bảo hiểm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceEventListener {

    private final InsuranceDeclarationService declarationService;

    /**
     * Khi tạo HĐ chính thức (LABOR_DEFINITE, LABOR_INDEFINITE) → tự động báo tăng BH.
     * Chỉ trigger cho HĐ lao động, không trigger cho cam kết hoặc thử việc.
     */
    @EventListener
    @Transactional
    public void onContractCreated(ContractCreatedEvent event) {
        String code = event.getContractTypeCode();

        // Chỉ báo tăng cho HĐ lao động chính thức
        if (code != null && (code.startsWith("LABOR_DEFINITE") || code.startsWith("LABOR_INDEFINITE"))) {
            log.info("Auto-creating INCREASE declaration for employee {} (contract type: {})",
                    event.getEmployeeId(), code);

            declarationService.createIncreaseDeclaration(
                    event.getCompanyId(),
                    event.getEmployeeId(),
                    event.getEffectiveFrom(),
                    "Tự động: Ký HĐ lao động " + code
            );
        }
    }

    /**
     * Khi NV nghỉ việc → tự động báo giảm BH.
     * Logic mốc ngày 15 đã được xử lý trong InsuranceDeclarationService.
     */
    @EventListener
    @Transactional
    public void onEmployeeTerminated(EmployeeTerminatedEvent event) {
        log.info("Auto-creating DECREASE declaration for employee {} (last day: {})",
                event.getEmployeeId(), event.getLastWorkingDay());

        declarationService.createDecreaseDeclaration(
                event.getCompanyId(),
                event.getEmployeeId(),
                event.getLastWorkingDay(),
                event.getReason() != null ? "Tự động: " + event.getReason() : "Tự động: Nhân viên nghỉ việc"
        );
    }
}
