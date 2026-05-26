package com.hrms.domain.alert.collector;

import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.common.enums.*;
import com.hrms.domain.contract.entity.ContractType;
import com.hrms.domain.contract.entity.EmployeeContract;
import com.hrms.domain.contract.repository.ContractTypeRepository;
import com.hrms.domain.contract.repository.EmployeeContractRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ContractAlertCollector implements AlertCollector {

    private final EmployeeContractRepository contractRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public AlertCategory getCategory() { return AlertCategory.CONTRACT; }

    @Override
    public NotificationTemplateType getNotificationType() { return NotificationTemplateType.CONTRACT_EXPIRY; }

    @Override
    public List<Alert> collect(Long companyId) {
        List<Alert> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // HĐ đã hết hạn nhưng chưa gia hạn/terminate → URGENT
        List<EmployeeContract> overdue = contractRepository.findOverdue(companyId, today);
        for (EmployeeContract c : overdue) {
            String empName = getEmployeeName(c.getEmployeeId(), companyId);
            String typeName = getContractTypeName(c.getContractTypeId(), companyId);
            long daysOverdue = ChronoUnit.DAYS.between(c.getEffectiveTo(), today);

            alerts.add(Alert.builder()
                    .companyId(companyId)
                    .alertKey(Alert.buildKey(AlertCategory.CONTRACT, "CONTRACT", c.getId()))
                    .category(AlertCategory.CONTRACT)
                    .severity(AlertSeverity.URGENT)
                    .title("HĐ đã hết hạn: " + empName)
                    .description(typeName + " — hết hạn " + c.getEffectiveTo() + " (quá " + daysOverdue + " ngày)")
                    .referenceType("CONTRACT")
                    .referenceId(c.getId())
                    .actionUrl("/contracts")
                    .dueDate(c.getEffectiveTo())
                    .status(AlertStatus.ACTIVE)
                    .build());
        }

        // HĐ sắp hết hạn (dùng reminderDaysBefore từ ContractType, mặc định 30) → WARNING
        // Lấy tất cả contract types để biết reminder days
        var contractTypes = contractTypeRepository.findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(companyId);
        int defaultDays = 30;
        int maxDays = contractTypes.stream()
                .mapToInt(ContractType::getReminderDaysBefore)
                .max().orElse(defaultDays);

        List<EmployeeContract> expiring = contractRepository.findExpiringSoon(companyId, today, today.plusDays(maxDays));
        for (EmployeeContract c : expiring) {
            String empName = getEmployeeName(c.getEmployeeId(), companyId);
            String typeName = getContractTypeName(c.getContractTypeId(), companyId);
            long daysLeft = ChronoUnit.DAYS.between(today, c.getEffectiveTo());

            alerts.add(Alert.builder()
                    .companyId(companyId)
                    .alertKey(Alert.buildKey(AlertCategory.CONTRACT, "CONTRACT", c.getId()))
                    .category(AlertCategory.CONTRACT)
                    .severity(AlertSeverity.WARNING)
                    .title("HĐ hết hạn " + daysLeft + " ngày: " + empName)
                    .description(typeName + " — hết hạn " + c.getEffectiveTo())
                    .referenceType("CONTRACT")
                    .referenceId(c.getId())
                    .actionUrl("/contracts")
                    .dueDate(c.getEffectiveTo())
                    .status(AlertStatus.ACTIVE)
                    .build());
        }

        return alerts;
    }

    @Override
    public void resolveCompleted(Long companyId, List<Alert> activeAlerts) {
        Set<Long> alertedIds = activeAlerts.stream()
                .filter(a -> "CONTRACT".equals(a.getReferenceType()))
                .map(Alert::getReferenceId)
                .collect(Collectors.toSet());

        for (Long contractId : alertedIds) {
            contractRepository.findByIdAndCompanyIdAndDeletedAtIsNull(contractId, companyId).ifPresent(contract -> {
                // HĐ đã gia hạn (effectiveTo > today) hoặc đã TERMINATED → resolve
                if (contract.getStatus() == ContractStatus.TERMINATED
                        || contract.getStatus() == ContractStatus.EXPIRED
                        || (contract.getEffectiveTo() != null && contract.getEffectiveTo().isAfter(LocalDate.now().plusDays(30)))) {
                    activeAlerts.stream()
                            .filter(a -> contractId.equals(a.getReferenceId()) && "CONTRACT".equals(a.getReferenceType()))
                            .forEach(a -> a.setStatus(AlertStatus.RESOLVED));
                }
            });
        }
    }

    @Override
    public String buildEmailSection(List<Alert> alerts) {
        if (alerts.isEmpty()) return "";
        long urgent = alerts.stream().filter(a -> a.getSeverity() == AlertSeverity.URGENT).count();
        long warning = alerts.stream().filter(a -> a.getSeverity() == AlertSeverity.WARNING).count();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-bottom:20px;'>");
        sb.append("<h3 style='color:#b45309;margin:0 0 8px;'>Hợp đồng");
        if (urgent > 0) sb.append(" — ").append(urgent).append(" đã hết hạn");
        if (warning > 0) sb.append(", ").append(warning).append(" sắp hết hạn");
        sb.append("</h3>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
        sb.append("<tr style='background:#f3f4f6;'><th style='padding:6px 8px;text-align:left;'>Nhân viên</th>");
        sb.append("<th style='padding:6px 8px;'>Mức độ</th><th style='padding:6px 8px;'>Ngày hết hạn</th></tr>");
        for (Alert a : alerts) {
            String color = a.getSeverity() == AlertSeverity.URGENT ? "#dc2626" : "#d97706";
            sb.append("<tr style='border-bottom:1px solid #e5e7eb;'>");
            sb.append("<td style='padding:6px 8px;'>").append(a.getTitle()).append("</td>");
            sb.append("<td style='padding:6px 8px;text-align:center;color:").append(color).append(";font-weight:bold;'>")
                    .append(a.getSeverity().name()).append("</td>");
            sb.append("<td style='padding:6px 8px;text-align:center;'>").append(a.getDueDate()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private String getEmployeeName(Long empId, Long companyId) {
        return employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(empId, companyId)
                .map(Employee::getFullName).orElse("N/A");
    }

    private String getContractTypeName(Long typeId, Long companyId) {
        return contractTypeRepository.findByIdAndCompanyId(typeId, companyId)
                .map(ContractType::getName).orElse("N/A");
    }
}
