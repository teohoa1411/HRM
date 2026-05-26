package com.hrms.domain.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;

/**
 * Event phát ra khi tạo hợp đồng lao động mới.
 * InsuranceEventListener lắng nghe để tự động tạo báo tăng BH.
 */
@Getter
public class ContractCreatedEvent extends ApplicationEvent {

    private final Long companyId;
    private final Long employeeId;
    private final Long contractTypeId;
    private final String contractTypeCode;
    private final LocalDate effectiveFrom;

    public ContractCreatedEvent(Object source, Long companyId, Long employeeId,
                                 Long contractTypeId, String contractTypeCode,
                                 LocalDate effectiveFrom) {
        super(source);
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.contractTypeId = contractTypeId;
        this.contractTypeCode = contractTypeCode;
        this.effectiveFrom = effectiveFrom;
    }
}
