package com.hrms.domain.asset.mapper;

import com.hrms.domain.asset.dto.AssetResponse;
import com.hrms.domain.asset.entity.Asset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "ownerVendorName", ignore = true)
    @Mapping(target = "supplierVendorName", ignore = true)
    @Mapping(target = "currentHolderName", ignore = true)
    @Mapping(target = "currentHolderId", ignore = true)
    @Mapping(target = "isRented", source = "rented")
    @Mapping(target = "ownershipType", expression = "java(entity.getOwnershipType().name())")
    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    @Mapping(target = "assetCondition", expression = "java(entity.getAssetCondition().name())")
    AssetResponse toResponse(Asset entity);
}
