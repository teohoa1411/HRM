package com.hrms.domain.asset.service;

import com.hrms.domain.asset.dto.AssetCategoryResponse;
import com.hrms.domain.asset.dto.CreateAssetCategoryRequest;
import com.hrms.domain.asset.entity.AssetCategory;
import com.hrms.domain.asset.mapper.AssetCategoryMapper;
import com.hrms.domain.asset.repository.AssetCategoryRepository;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetCategoryService {

    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetCategoryMapper assetCategoryMapper;

    @Transactional(readOnly = true)
    public Page<AssetCategoryResponse> listByCompany(Long companyId, Pageable pageable) {
        return assetCategoryRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
                .map(category -> {
                    long assetCount = assetCategoryRepository.countAssetsByCategory(category.getId());
                    return new AssetCategoryResponse(
                            category.getId(),
                            category.getCode(),
                            category.getName(),
                            category.getDescription(),
                            category.isActive(),
                            assetCount
                    );
                });
    }

    @Transactional(readOnly = true)
    public List<AssetCategoryResponse> listActive(Long companyId) {
        return assetCategoryRepository.findByCompanyIdAndActiveIsTrueAndDeletedAtIsNull(companyId)
                .stream()
                .map(category -> {
                    long assetCount = assetCategoryRepository.countAssetsByCategory(category.getId());
                    return new AssetCategoryResponse(
                            category.getId(),
                            category.getCode(),
                            category.getName(),
                            category.getDescription(),
                            category.isActive(),
                            assetCount
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetCategoryResponse getById(Long id, Long companyId) {
        AssetCategory category = assetCategoryRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("AssetCategory", id));
        long assetCount = assetCategoryRepository.countAssetsByCategory(id);
        return new AssetCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                assetCount
        );
    }

    @Transactional
    public AssetCategoryResponse create(CreateAssetCategoryRequest request, Long companyId) {
        if (assetCategoryRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.code(), companyId)) {
            throw new DuplicateResourceException("Asset category code already exists in this company: " + request.code());
        }

        AssetCategory category = AssetCategory.builder()
                .companyId(companyId)
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .active(request.active() != null ? request.active() : true)
                .build();

        category = assetCategoryRepository.save(category);
        log.info("Asset category created: {} for company: {}", category.getId(), companyId);

        return new AssetCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                0
        );
    }

    @Transactional
    public AssetCategoryResponse update(Long id, CreateAssetCategoryRequest request, Long companyId) {
        AssetCategory category = assetCategoryRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("AssetCategory", id));

        if (!category.getCode().equals(request.code()) &&
                assetCategoryRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.code(), companyId)) {
            throw new DuplicateResourceException("Asset category code already exists in this company: " + request.code());
        }

        category.setCode(request.code());
        category.setName(request.name());
        category.setDescription(request.description());
        if (request.active() != null) {
            category.setActive(request.active());
        }

        category = assetCategoryRepository.save(category);
        log.info("Asset category updated: {} for company: {}", id, companyId);

        long assetCount = assetCategoryRepository.countAssetsByCategory(id);
        return new AssetCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                assetCount
        );
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        AssetCategory category = assetCategoryRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("AssetCategory", id));
        category.softDelete();
        assetCategoryRepository.save(category);
        log.info("Asset category deleted (soft): {} for company: {}", id, companyId);
    }
}
