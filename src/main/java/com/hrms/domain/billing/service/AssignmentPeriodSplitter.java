package com.hrms.domain.billing.service;

import com.hrms.domain.assignment.entity.EmployeeAssignment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tách một tháng thành các đoạn (period) liên tiếp, mỗi period gắn với 1 customer
 * (hoặc null nếu là gap không có phân công).
 *
 * <p>Ràng buộc nghiệp vụ: 1 NV / 1 ngày = tối đa 1 customer (xem
 * {@code no_overlap_per_employee} exclusion constraint trên bảng employee_assignments).
 * Vì vậy thuật toán không phải resolve conflict.</p>
 *
 * <p>Output luôn cover hết [monthStart, monthEnd], các period không overlap và
 * sắp xếp tăng dần theo {@code start}.</p>
 */
public final class AssignmentPeriodSplitter {

    private AssignmentPeriodSplitter() {}

    @Getter
    @AllArgsConstructor
    public static final class Period {
        private final LocalDate start;
        private final LocalDate end;
        /** Customer phụ trách trong period; {@code null} nếu không có assignment (gap). */
        private final Long customerId;
        /** ID của assignment gốc; {@code null} cho gap. */
        private final Long assignmentId;

        public boolean isGap() { return customerId == null; }
    }

    /**
     * Tách [monthStart, monthEnd] dựa trên các assignment đã được lọc overlap.
     *
     * @param assignments  list assignment ĐÃ lọc overlap với tháng (xem
     *                     {@code EmployeeAssignmentRepository.findOverlapping}).
     * @param monthStart   ngày đầu tháng (ví dụ 2026-04-01)
     * @param monthEnd     ngày cuối tháng (ví dụ 2026-04-30)
     * @return danh sách period theo thứ tự thời gian, cover hết tháng.
     */
    public static List<Period> split(List<EmployeeAssignment> assignments,
                                     LocalDate monthStart, LocalDate monthEnd) {
        if (monthStart == null || monthEnd == null || monthStart.isAfter(monthEnd)) {
            return Collections.emptyList();
        }

        // Sort theo startDate (defensive — repository đã ORDER BY nhưng vẫn đề phòng caller khác)
        List<EmployeeAssignment> sorted = new ArrayList<>(assignments);
        sorted.sort((a, b) -> a.getStartDate().compareTo(b.getStartDate()));

        List<Period> periods = new ArrayList<>();
        LocalDate cursor = monthStart;

        for (EmployeeAssignment a : sorted) {
            LocalDate aStart = a.getStartDate().isBefore(monthStart) ? monthStart : a.getStartDate();
            LocalDate aEnd   = (a.getEndDate() == null || a.getEndDate().isAfter(monthEnd))
                    ? monthEnd : a.getEndDate();

            // Bỏ qua assignment không thực sự overlap tháng (defensive — caller filter rồi)
            if (aEnd.isBefore(monthStart) || aStart.isAfter(monthEnd)) continue;

            // Nếu có gap trước assignment này → tạo period gap (customerId = null)
            if (cursor.isBefore(aStart)) {
                periods.add(new Period(cursor, aStart.minusDays(1), null, null));
            }

            // Period thực gắn với assignment
            periods.add(new Period(aStart, aEnd, a.getCustomerId(), a.getId()));
            cursor = aEnd.plusDays(1);

            if (cursor.isAfter(monthEnd)) break;
        }

        // Gap cuối tháng (nếu assignment cuối kết thúc trước monthEnd)
        if (!cursor.isAfter(monthEnd)) {
            periods.add(new Period(cursor, monthEnd, null, null));
        }

        return periods;
    }
}
