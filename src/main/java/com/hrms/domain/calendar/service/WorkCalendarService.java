package com.hrms.domain.calendar.service;

import com.hrms.domain.calendar.dto.CreateWorkCalendarRequest;
import com.hrms.domain.calendar.dto.WorkCalendarResponse;
import com.hrms.domain.calendar.entity.WorkCalendar;
import com.hrms.domain.calendar.entity.WorkCalendarDetail;
import com.hrms.domain.calendar.repository.WorkCalendarRepository;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.infrastructure.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarRepository calendarRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<WorkCalendarResponse> listByCompany(Long companyId) {
        return calendarRepository.findByCompanyIdOrderByYearMonthDesc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkCalendarResponse getByMonth(Long companyId, String yearMonth) {
        return calendarRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("WorkCalendar for " + yearMonth));
    }

    @Transactional(readOnly = true)
    public Optional<WorkCalendar> findForMonth(Long companyId, String yearMonth) {
        return calendarRepository.findByCompanyIdAndYearMonth(companyId, yearMonth);
    }

    @Transactional
    public WorkCalendarResponse create(Long companyId, CreateWorkCalendarRequest request) {
        if (calendarRepository.existsByCompanyIdAndYearMonth(companyId, request.yearMonth())) {
            throw new BusinessException("Work calendar already exists for " + request.yearMonth());
        }

        Long userId = SecurityUtils.getCurrentUserId();
        WorkCalendar calendar = WorkCalendar.builder()
                .companyId(companyId)
                .yearMonth(request.yearMonth())
                .totalWorkdays(request.totalWorkdays() != null ? request.totalWorkdays() : 0.0)
                .description(request.description())
                .isDefault(request.isDefault())
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        if (request.details() != null) {
            request.details().forEach(d -> {
                WorkCalendarDetail detail = WorkCalendarDetail.builder()
                        .calendar(calendar)
                        .workDate(d.workDate())
                        .dayType(d.dayType())
                        .note(d.note())
                        .build();
                calendar.getDetails().add(detail);
            });
        }

        return toResponse(calendarRepository.save(calendar));
    }

    @Transactional
    public WorkCalendarResponse update(Long id, Long companyId, CreateWorkCalendarRequest request) {
        WorkCalendar calendar = calendarRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("WorkCalendar", id));

        calendar.setTotalWorkdays(request.totalWorkdays() != null ? request.totalWorkdays() : 0.0);
        calendar.setDescription(request.description());
        calendar.setDefault(request.isDefault());
        calendar.setUpdatedBy(SecurityUtils.getCurrentUserId());

        // Replace all details — flush DELETE trước để tránh duplicate key với INSERT mới
        calendar.getDetails().clear();
        entityManager.flush();
        if (request.details() != null) {
            request.details().forEach(d -> {
                WorkCalendarDetail detail = WorkCalendarDetail.builder()
                        .calendar(calendar)
                        .workDate(d.workDate())
                        .dayType(d.dayType())
                        .note(d.note())
                        .build();
                calendar.getDetails().add(detail);
            });
        }

        return toResponse(calendarRepository.save(calendar));
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        WorkCalendar calendar = calendarRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("WorkCalendar", id));
        calendarRepository.delete(calendar);
    }

    /**
     * Copy lịch mặc định (is_default=true) sang một tháng mới.
     * Dùng khi admin muốn áp dụng cùng lịch cho nhiều tháng.
     */
    @Transactional
    public WorkCalendarResponse applyDefaultToMonth(Long companyId, String targetYearMonth) {
        // Tìm lịch mặc định
        WorkCalendar defaultCal = calendarRepository.findByCompanyIdOrderByYearMonthDesc(companyId)
                .stream().filter(WorkCalendar::isDefault).findFirst()
                .orElseThrow(() -> new BusinessException("Chưa có lịch mặc định. Hãy đánh dấu một lịch là mặc định trước."));

        if (calendarRepository.existsByCompanyIdAndYearMonth(companyId, targetYearMonth)) {
            throw new BusinessException("Tháng " + targetYearMonth + " đã có lịch. Hãy xóa trước khi áp dụng mặc định.");
        }

        // Parse target month để re-map ngày
        String[] parts = targetYearMonth.split("-");
        int targetYear = Integer.parseInt(parts[0]);
        int targetMonth = Integer.parseInt(parts[1]);

        Long userId = SecurityUtils.getCurrentUserId();
        WorkCalendar newCal = WorkCalendar.builder()
                .companyId(companyId)
                .yearMonth(targetYearMonth)
                .totalWorkdays(defaultCal.getTotalWorkdays())
                .description("Áp dụng từ lịch mặc định " + defaultCal.getYearMonth())
                .isDefault(false)
                .createdBy(userId).updatedBy(userId)
                .build();

        // Re-map ngày theo pattern của lịch mặc định (T2-T6 workday, T7-CN weekend)
        // Tự động generate theo DOW pattern từ lịch nguồn
        int daysInTarget = java.time.YearMonth.of(targetYear, targetMonth).lengthOfMonth();
        double workdayCount = 0;
        for (int day = 1; day <= daysInTarget; day++) {
            java.time.LocalDate date = java.time.LocalDate.of(targetYear, targetMonth, day);
            int dow = date.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
            WorkCalendarDetail.DayType dayType = (dow == 6 || dow == 7)
                    ? WorkCalendarDetail.DayType.WEEKEND
                    : WorkCalendarDetail.DayType.FULL_DAY;
            if (dayType == WorkCalendarDetail.DayType.FULL_DAY) workdayCount += 1.0;

            WorkCalendarDetail detail = WorkCalendarDetail.builder()
                    .calendar(newCal).workDate(date).dayType(dayType).build();
            newCal.getDetails().add(detail);
        }
        newCal.setTotalWorkdays(workdayCount);

        return toResponse(calendarRepository.save(newCal));
    }

    /**
     * Check if a date is a valid workday in the calendar.
     * Returns true if calendar doesn't exist (no restriction) or if the date is WORKDAY.
     */
    public boolean isWorkday(Long companyId, LocalDate date) {
        String yearMonth = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        return calendarRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .map(cal -> cal.getDetails().stream()
                        .filter(d -> d.getWorkDate().equals(date))
                        .findFirst()
                        .map(d -> d.getDayType() == WorkCalendarDetail.DayType.FULL_DAY
                                  || d.getDayType() == WorkCalendarDetail.DayType.MORNING
                                  || d.getDayType() == WorkCalendarDetail.DayType.AFTERNOON)
                        .orElse(true)) // date not listed = assume workday
                .orElse(true); // no calendar = no restriction
    }

    /**
     * Get total workdays in month from calendar, or default to 22.
     */
    public double getTotalWorkdays(Long companyId, String yearMonth) {
        return calendarRepository.findByCompanyIdAndYearMonth(companyId, yearMonth)
                .map(WorkCalendar::getTotalWorkdays)
                .orElse(22.0);
    }

    private WorkCalendarResponse toResponse(WorkCalendar c) {
        List<WorkCalendarResponse.DetailItem> details = c.getDetails().stream()
                .map(d -> new WorkCalendarResponse.DetailItem(d.getId(), d.getWorkDate(), d.getDayType(), d.getNote()))
                .toList();
        return new WorkCalendarResponse(c.getId(), c.getCompanyId(), c.getYearMonth(),
                c.getTotalWorkdays(), c.getDescription(), c.isDefault(), c.getCreatedAt(), details);
    }
}
