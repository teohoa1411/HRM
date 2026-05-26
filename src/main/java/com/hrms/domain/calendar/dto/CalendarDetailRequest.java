package com.hrms.domain.calendar.dto;

import com.hrms.domain.calendar.entity.WorkCalendarDetail.DayType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CalendarDetailRequest(
        @NotNull LocalDate workDate,
        @NotNull DayType dayType,
        String note
) {}
