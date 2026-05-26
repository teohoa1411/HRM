package com.hrms.domain.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;

/**
 * Event phát ra khi nhân viên nghỉ việc (status → TERMINATED).
 * InsuranceEventListener lắng nghe để tự động tạo báo giảm BH.
 */
@Getter
public class EmployeeTerminatedEvent extends ApplicationEvent {

    private final Long companyId;
    private final Long employeeId;
    private final LocalDate lastWorkingDay;
    private final String reason;

    public EmployeeTerminatedEvent(Object source, Long companyId, Long employeeId,
                                    LocalDate lastWorkingDay, String reason) {
        super(source);
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.lastWorkingDay = lastWorkingDay;
        this.reason = reason;
    }
}
