package com.hrms.domain.calendar.dto;

import com.hrms.domain.calendar.entity.WorkCalendarDetail.DayType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WorkCalendarResponse(
        Long id,
        Long companyId,
        String yearMonth,
        double totalWorkdays,
        String description,
        boolean isDefault,
        LocalDateTime createdAt,
        List<DetailItem> details
) {
    public record DetailItem(Long id, LocalDate workDate, DayType dayType, String note) {}
}
