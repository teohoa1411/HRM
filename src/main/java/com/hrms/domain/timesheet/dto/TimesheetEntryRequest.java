package com.hrms.domain.timesheet.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TimesheetEntryRequest(
        @NotBlank @Size(max = 50)
        String projectKey,

        @Size(max = 50)
        String projectId,

        @Size(max = 200)
        String projectName,

        @NotNull
        LocalDate workDate,

        @NotNull @DecimalMin("0") @DecimalMax("24")
        BigDecimal hours,

        @DecimalMin("0") @DecimalMax("24")
        BigDecimal otHours,

        @Size(max = 255)
        String note
) {}
