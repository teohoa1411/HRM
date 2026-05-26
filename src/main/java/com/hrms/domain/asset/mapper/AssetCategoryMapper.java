package com.hrms.domain.asset.mapper;

import com.hrms.domain.asset.dto.AssetCategoryResponse;
import com.hrms.domain.asset.entity.AssetCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AssetCategoryMapper {

    @Mapping(target = "assetCount", ignore = true)
    AssetCategoryResponse toResponse(AssetCategory entity);
}
