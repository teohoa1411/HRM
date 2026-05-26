package com.hrms.domain.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAssetCategoryRequest(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    Boolean active
) {}
