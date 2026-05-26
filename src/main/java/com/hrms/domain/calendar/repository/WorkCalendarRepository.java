package com.hrms.domain.calendar.repository;

import com.hrms.domain.calendar.entity.WorkCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, Long> {

    List<WorkCalendar> findByCompanyIdOrderByYearMonthDesc(Long companyId);

    Optional<WorkCalendar> findByCompanyIdAndYearMonth(Long companyId, String yearMonth);

    Optional<WorkCalendar> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndYearMonth(Long companyId, String yearMonth);
}
