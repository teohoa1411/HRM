package com.hrms.domain.asset.dto;

public record AssetCategoryResponse(
    Long id,
    String code,
    String name,
    String description,
    boolean active,
    long assetCount
) {}
