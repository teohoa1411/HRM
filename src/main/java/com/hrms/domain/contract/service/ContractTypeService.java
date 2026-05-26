package com.hrms.domain.contract.service;

import com.hrms.domain.common.enums.ContractCategory;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.contract.dto.ContractTypeRequest;
import com.hrms.domain.contract.dto.ContractTypeResponse;
import com.hrms.domain.contract.entity.ContractType;
import com.hrms.domain.contract.repository.ContractTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractTypeService {

    private final ContractTypeRepository repository;

    @Transactional(readOnly = true)
    public List<ContractTypeResponse> getActiveTypes(Long companyId) {
        return repository.findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractTypeResponse> getAllTypes(Long companyId) {
        return repository.findByCompanyIdOrderBySortOrderAscNameAsc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractTypeResponse> getByCategory(Long companyId, ContractCategory category) {
        return repository.findByCompanyIdAndCategoryAndActiveTrueOrderBySortOrderAsc(companyId, category)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContractTypeResponse create(Long companyId, ContractTypeRequest request) {
        if (repository.existsByCodeAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("Contract type code already exists: " + request.code());
        }

        ContractType entity = ContractType.builder()
                .companyId(companyId)
                .code(request.code())
                .name(request.name())
                .category(request.category())
                .hasExpiry(request.hasExpiry())
                .defaultDurationMonths(request.defaultDurationMonths())
                .reminderDaysBefore(request.reminderDaysBefore() != null ? request.reminderDaysBefore() : 30)
                .description(request.description())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        return toResponse(repository.save(entity));
    }

    @Transactional
    public ContractTypeResponse update(Long id, Long companyId, ContractTypeRequest request) {
        ContractType entity = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("ContractType", id));

        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setHasExpiry(request.hasExpiry());
        entity.setDefaultDurationMonths(request.defaultDurationMonths());
        if (request.reminderDaysBefore() != null) {
            entity.setReminderDaysBefore(request.reminderDaysBefore());
        }
        entity.setDescription(request.description());
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void toggleActive(Long id, Long companyId) {
        ContractType entity = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("ContractType", id));
        entity.setActive(!entity.isActive());
        repository.save(entity);
    }

    private ContractTypeResponse toResponse(ContractType e) {
        return new ContractTypeResponse(
                e.getId(), e.getCode(), e.getName(), e.getCategory(),
                e.isHasExpiry(), e.getDefaultDurationMonths(), e.getReminderDaysBefore(),
                e.getDescription(), e.isActive(), e.getSortOrder(), e.getCreatedAt());
    }
}
