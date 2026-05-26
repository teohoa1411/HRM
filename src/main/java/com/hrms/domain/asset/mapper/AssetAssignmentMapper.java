package com.hrms.domain.asset.mapper;

import com.hrms.domain.asset.dto.AssetAssignmentResponse;
import com.hrms.domain.asset.entity.AssetAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AssetAssignmentMapper {

    @Mapping(target = "assetCode", ignore = true)
    @Mapping(target = "assetName", ignore = true)
    @Mapping(target = "serialNumber", ignore = true)
    @Mapping(target = "employeeName", ignore = true)
    @Mapping(target = "employeeCode", ignore = true)
    @Mapping(target = "assignedByName", ignore = true)
    @Mapping(target = "conditionOnAssign", expression = "java(entity.getConditionOnAssign().name())")
    @Mapping(target = "conditionOnReturn", expression = "java(entity.getConditionOnReturn() != null ? entity.getConditionOnReturn().name() : null)")
    AssetAssignmentResponse toResponse(AssetAssignment entity);
}
