package com.hrms.domain.company.repository;

import com.hrms.domain.company.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    Optional<Position> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);
}
