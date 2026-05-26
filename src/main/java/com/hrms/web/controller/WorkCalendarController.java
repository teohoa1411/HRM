package com.hrms.web.controller;

import com.hrms.domain.calendar.dto.CreateWorkCalendarRequest;
import com.hrms.domain.calendar.dto.WorkCalendarResponse;
import com.hrms.domain.calendar.service.WorkCalendarService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/work-calendars")
@RequiredArgsConstructor
@Tag(name = "Work Calendars", description = "Monthly work calendar management")
public class WorkCalendarController {

    private final WorkCalendarService calendarService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all work calendars for current company")
    public ResponseEntity<ApiResponse<List<WorkCalendarResponse>>> list() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(calendarService.listByCompany(companyId)));
    }

    @GetMapping("/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('EMPLOYEE')")
    @Operation(summary = "Get calendar for specific month (YYYY-MM)")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> getByMonth(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(calendarService.getByMonth(companyId, yearMonth)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create work calendar for a month")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> create(
            @Valid @RequestBody CreateWorkCalendarRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(calendarService.create(companyId, request), "Work calendar created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update work calendar")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CreateWorkCalendarRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(calendarService.update(id, companyId, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete work calendar")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        calendarService.delete(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Work calendar deleted"));
    }

    @PostMapping("/apply-default/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Apply default calendar pattern to a new month (YYYY-MM)")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> applyDefault(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(calendarService.applyDefaultToMonth(companyId, yearMonth),
                        "ÄÃ£ Ã¡p dá»¥ng lá»‹ch máº·c Ä‘á»‹nh cho thÃ¡ng " + yearMonth));
    }
}
