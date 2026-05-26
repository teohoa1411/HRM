package com.hrms.domain.company.service;

import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.company.dto.PositionRequest;
import com.hrms.domain.company.dto.PositionResponse;
import com.hrms.domain.company.entity.Position;
import com.hrms.domain.company.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    @Transactional(readOnly = true)
    public List<PositionResponse> list(Long companyId) {
        return positionRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PositionResponse getById(Long id, Long companyId) {
        return toResponse(positionRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Position", id)));
    }

    @Transactional
    public PositionResponse create(PositionRequest request, Long companyId) {
        Position pos = Position.builder()
                .companyId(companyId)
                .code(request.code())
                .name(request.name())
                .level(request.level() > 0 ? request.level() : 1)
                .description(request.description())
                .active(true)
                .build();
        return toResponse(positionRepository.save(pos));
    }

    @Transactional
    public PositionResponse update(Long id, PositionRequest request, Long companyId) {
        Position pos = positionRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Position", id));
        pos.setName(request.name());
        pos.setLevel(request.level());
        pos.setDescription(request.description());
        return toResponse(positionRepository.save(pos));
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        Position pos = positionRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Position", id));
        pos.softDelete();
        positionRepository.save(pos);
    }

    private PositionResponse toResponse(Position p) {
        return new PositionResponse(p.getId(), p.getCompanyId(), p.getCode(),
                p.getName(), p.getLevel(), p.getDescription(), p.isActive());
    }
}
