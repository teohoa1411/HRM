package com.hrms.domain.timesheet.dto;

import com.hrms.domain.timesheet.entity.CompanyTimesheetField;

public record CompanyTimesheetFieldResponse(
        Long id,
        String fieldName,
        String fieldKey,
        String fieldType,
        boolean required,
        int sortOrder,
        boolean active
) {
    public static CompanyTimesheetFieldResponse from(CompanyTimesheetField f) {
        return new CompanyTimesheetFieldResponse(
                f.getId(), f.getFieldName(), f.getFieldKey(),
                f.getFieldType(), f.isRequired(), f.getSortOrder(), f.isActive());
    }
}
