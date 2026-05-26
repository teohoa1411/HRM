package com.hrms.domain.jobrole.mapper;

import com.hrms.domain.jobrole.dto.JobRoleResponse;
import com.hrms.domain.jobrole.entity.JobRole;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobRoleMapper {
    JobRoleResponse toResponse(JobRole jobRole);
}
