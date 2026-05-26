package com.hrms.domain.calendar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "work_calendar_details",
        uniqueConstraints = @UniqueConstraint(columnNames = {"calendar_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkCalendarDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private WorkCalendar calendar;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    @Builder.Default
    private DayType dayType = DayType.FULL_DAY;  // default cho ngày thường

    @Column(name = "note", length = 255)
    private String note;

    public enum DayType {
        /** Làm cả ngày – 1.0 công */
        FULL_DAY,
        /** Làm buổi sáng – 0.5 công */
        MORNING,
        /** Làm buổi chiều – 0.5 công */
        AFTERNOON,
        /** Nghỉ cuối tuần (T7/CN) – 0 công, hệ số OT cuối tuần */
        WEEKEND,
        /** Nghỉ lễ – 0 công, hệ số OT ngày lễ */
        HOLIDAY
    }
}
