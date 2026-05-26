package com.hrms.domain.alert.service;

import com.hrms.domain.alert.dto.AlertCountDto;
import com.hrms.domain.alert.dto.AlertDto;
import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.alert.repository.AlertRepository;
import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.domain.common.enums.AlertSeverity;
import com.hrms.domain.common.enums.AlertStatus;
import com.hrms.domain.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public List<AlertDto> getActiveAlerts(Long companyId) {
        return alertRepository.findByCompanyIdAndStatusOrderBySeverityAscDueDateAsc(companyId, AlertStatus.ACTIVE)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> getByCategory(Long companyId, AlertCategory category) {
        return alertRepository.findByCompanyIdAndCategoryAndStatusOrderByDueDateAsc(companyId, category, AlertStatus.ACTIVE)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AlertCountDto getCounts(Long companyId) {
        long urgent = alertRepository.countByCompanyIdAndStatusAndSeverity(companyId, AlertStatus.ACTIVE, AlertSeverity.URGENT);
        long warning = alertRepository.countByCompanyIdAndStatusAndSeverity(companyId, AlertStatus.ACTIVE, AlertSeverity.WARNING);
        long info = alertRepository.countByCompanyIdAndStatusAndSeverity(companyId, AlertStatus.ACTIVE, AlertSeverity.INFO);

        // Count by category
        List<AlertDto> all = getActiveAlerts(companyId);
        long insurance = all.stream().filter(a -> a.category() == AlertCategory.INSURANCE).count();
        long contract = all.stream().filter(a -> a.category() == AlertCategory.CONTRACT).count();

        return new AlertCountDto(urgent, warning, info, urgent + warning + info, insurance, contract);
    }

    @Transactional
    public void dismiss(Long id, Long companyId, Long userId) {
        Alert alert = alertRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Alert", id));
        alert.setStatus(AlertStatus.DISMISSED);
        alert.setDismissedAt(LocalDateTime.now());
        alert.setDismissedBy(userId);
        alertRepository.save(alert);
    }

    @Transactional
    public void resolve(Long id, Long companyId) {
        Alert alert = alertRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Alert", id));
        alert.setStatus(AlertStatus.RESOLVED);
        alertRepository.save(alert);
    }

    /**
     * Upsert: INSERT nếu chưa tồn tại, SKIP nếu đã có.
     * Trả về số alerts mới được tạo.
     */
    @Transactional
    public int upsertAlerts(Long companyId, List<Alert> newAlerts) {
        int created = 0;
        for (Alert alert : newAlerts) {
            var existing = alertRepository.findByCompanyIdAndAlertKey(companyId, alert.getAlertKey());
            if (existing.isEmpty()) {
                alertRepository.save(alert);
                created++;
            } else {
                // Alert đã tồn tại — chỉ update severity nếu thay đổi (VD: WARNING → URGENT)
                Alert ex = existing.get();
                if (ex.getStatus() == AlertStatus.ACTIVE && ex.getSeverity() != alert.getSeverity()) {
                    ex.setSeverity(alert.getSeverity());
                    ex.setTitle(alert.getTitle());
                    ex.setDescription(alert.getDescription());
                    ex.setDueDate(alert.getDueDate());
                    alertRepository.save(ex);
                }
                // DISMISSED / RESOLVED → skip hoàn toàn
            }
        }
        return created;
    }

    /**
     * Auto-resolve: cập nhật alerts đã được collector resolve.
     */
    @Transactional
    public int saveResolvedAlerts(List<Alert> resolvedAlerts) {
        int count = 0;
        for (Alert a : resolvedAlerts) {
            if (a.getId() != null && a.getStatus() == AlertStatus.RESOLVED) {
                alertRepository.save(a);
                count++;
            }
        }
        return count;
    }

    /** Cleanup alerts cũ (DISMISSED/RESOLVED > 90 ngày) */
    @Transactional
    public int cleanup(Long companyId) {
        return alertRepository.cleanupOld(companyId, LocalDateTime.now().minusDays(90));
    }

    private AlertDto toDto(Alert a) {
        return new AlertDto(a.getId(), a.getAlertKey(), a.getCategory(), a.getSeverity(),
                a.getTitle(), a.getDescription(), a.getReferenceType(), a.getReferenceId(),
                a.getActionUrl(), a.getDueDate(), a.getStatus(), a.getCreatedAt());
    }
}
