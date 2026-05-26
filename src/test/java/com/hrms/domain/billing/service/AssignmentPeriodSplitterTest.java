package com.hrms.domain.billing.service;

import com.hrms.domain.assignment.entity.EmployeeAssignment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentPeriodSplitterTest {

    private static final LocalDate APR_1  = LocalDate.of(2026, 4, 1);
    private static final LocalDate APR_30 = LocalDate.of(2026, 4, 30);

    private static EmployeeAssignment a(long id, Long customerId, LocalDate s, LocalDate e) {
        EmployeeAssignment ea = new EmployeeAssignment();
        ea.setId(id);
        ea.setCustomerId(customerId);
        ea.setStartDate(s);
        ea.setEndDate(e);
        return ea;
    }

    @Test
    void emptyAssignments_oneGapPeriod() {
        List<AssignmentPeriodSplitter.Period> ps =
                AssignmentPeriodSplitter.split(List.of(), APR_1, APR_30);
        assertEquals(1, ps.size());
        assertTrue(ps.get(0).isGap());
        assertEquals(APR_1, ps.get(0).getStart());
        assertEquals(APR_30, ps.get(0).getEnd());
    }

    @Test
    void singleAssignmentCoversWholeMonth_oneCustomerPeriod() {
        List<AssignmentPeriodSplitter.Period> ps = AssignmentPeriodSplitter.split(
                List.of(a(1L, 100L, LocalDate.of(2026, 1, 1), null)),
                APR_1, APR_30);
        assertEquals(1, ps.size());
        assertEquals(100L, ps.get(0).getCustomerId());
        assertEquals(APR_1, ps.get(0).getStart());
        assertEquals(APR_30, ps.get(0).getEnd());
    }

    @Test
    void midMonthSwitch_twoPeriodsBackToBack() {
        // Customer A đến 14/4, Customer B từ 15/4
        List<AssignmentPeriodSplitter.Period> ps = AssignmentPeriodSplitter.split(
                List.of(
                        a(1L, 100L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 14)),
                        a(2L, 200L, LocalDate.of(2026, 4, 15), null)
                ),
                APR_1, APR_30);
        assertEquals(2, ps.size());
        assertEquals(100L, ps.get(0).getCustomerId());
        assertEquals(LocalDate.of(2026, 4, 14), ps.get(0).getEnd());
        assertEquals(200L, ps.get(1).getCustomerId());
        assertEquals(LocalDate.of(2026, 4, 15), ps.get(1).getStart());
        assertEquals(APR_30, ps.get(1).getEnd());
    }

    @Test
    void assignmentStartsMidMonth_gapFirstThenCustomer() {
        // NV onboard 15/4, không có gì trước đó
        List<AssignmentPeriodSplitter.Period> ps = AssignmentPeriodSplitter.split(
                List.of(a(1L, 100L, LocalDate.of(2026, 4, 15), null)),
                APR_1, APR_30);
        assertEquals(2, ps.size());
        assertTrue(ps.get(0).isGap());
        assertEquals(APR_1, ps.get(0).getStart());
        assertEquals(LocalDate.of(2026, 4, 14), ps.get(0).getEnd());
        assertEquals(100L, ps.get(1).getCustomerId());
        assertEquals(LocalDate.of(2026, 4, 15), ps.get(1).getStart());
        assertEquals(APR_30, ps.get(1).getEnd());
    }

    @Test
    void assignmentEndsMidMonth_customerThenGap() {
        // NV nghỉ việc 20/4
        List<AssignmentPeriodSplitter.Period> ps = AssignmentPeriodSplitter.split(
                List.of(a(1L, 100L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 20))),
                APR_1, APR_30);
        assertEquals(2, ps.size());
        assertEquals(100L, ps.get(0).getCustomerId());
        assertEquals(LocalDate.of(2026, 4, 20), ps.get(0).getEnd());
        assertTrue(ps.get(1).isGap());
        assertEquals(LocalDate.of(2026, 4, 21), ps.get(1).getStart());
        assertEquals(APR_30, ps.get(1).getEnd());
    }

    @Test
    void gapBetweenTwoAssignments_threePeriodsWithMiddleGap() {
        // 1-9/4 customer 100, 10-19/4 không có gì, 20-30/4 customer 200
        List<AssignmentPeriodSplitter.Period> ps = AssignmentPeriodSplitter.split(
                List.of(
                        a(1L, 100L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 9)),
                        a(2L, 200L, LocalDate.of(2026, 4, 20), null)
                ),
                APR_1, APR_30);
        assertEquals(3, ps.size());
        assertEquals(100L, ps.get(0).getCustomerId());
        assertTrue(ps.get(1).isGap());
        assertEquals(LocalDate.of(2026, 4, 10), ps.get(1).getStart());
        assertEquals(LocalDate.of(2026, 4, 19), ps.get(1).getEnd());
        assertEquals(200L, ps.get(2).getCustomerId());
    }
}
