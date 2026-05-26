package com.hrms.domain.notification.repository;

import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByCompanyIdOrderBySentAtDesc(Long companyId);

    List<NotificationLog> findByCompanyIdAndTemplateTypeOrderBySentAtDesc(
            Long companyId, NotificationTemplateType templateType);

    List<NotificationLog> findByCompanyIdAndSentAtBetweenOrderBySentAtDesc(
            Long companyId, LocalDateTime from, LocalDateTime to);

    boolean existsByCompanyIdAndTemplateTypeAndReferenceIdAndSentAtAfter(
            Long companyId, NotificationTemplateType templateType, Long referenceId, LocalDateTime after);
}
