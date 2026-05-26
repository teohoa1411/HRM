package com.hrms.domain.employee.mapper;

import com.hrms.domain.employee.dto.EmployeeResponse;
import com.hrms.domain.employee.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "jobRoleName",   ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "username",      ignore = true)
    @Mapping(target = "vendorName",    ignore = true)
    @Mapping(target = "currentSalary", ignore = true)
    EmployeeResponse toResponse(Employee employee);
}
