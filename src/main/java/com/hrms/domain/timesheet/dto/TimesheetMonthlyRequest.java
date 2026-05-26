package com.hrms.domain.timesheet.dto;

public record TimesheetMonthlyRequest(
        String description,
        String additions  // raw JSON string of custom field values
) {}
