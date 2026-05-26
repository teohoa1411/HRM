package com.hrms.domain.notification.repository;

import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByCompanyId(Long companyId);

    Optional<NotificationSetting> findByCompanyIdAndTemplateType(
            Long companyId, NotificationTemplateType templateType);

    List<NotificationSetting> findByTemplateTypeAndEnabledTrue(NotificationTemplateType templateType);
}
