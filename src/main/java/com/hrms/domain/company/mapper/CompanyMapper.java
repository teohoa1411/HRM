package com.hrms.domain.company.mapper;

import com.hrms.domain.company.dto.CompanyResponse;
import com.hrms.domain.company.dto.DepartmentResponse;
import com.hrms.domain.company.entity.Company;
import com.hrms.domain.company.entity.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    @Mapping(target = "adminUsername", ignore = true)
    @Mapping(target = "adminEmail", ignore = true)
    @Mapping(target = "tempAdminPassword", ignore = true)
    CompanyResponse toResponse(Company company);

    DepartmentResponse toDepartmentResponse(Department department);
}
