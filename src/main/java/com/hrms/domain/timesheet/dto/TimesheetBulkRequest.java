package com.hrms.domain.timesheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TimesheetBulkRequest(
        @NotEmpty @Valid
        List<TimesheetEntryRequest> entries
) {}
