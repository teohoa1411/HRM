package com.hrms.domain.calendar.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateWorkCalendarRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "yearMonth must be YYYY-MM format")
        String yearMonth,

        @NotNull
        @DecimalMin(value = "0.0", message = "Total workdays must be >= 0")
        Double totalWorkdays,

        String description,
        boolean isDefault,

        List<CalendarDetailRequest> details
) {}
